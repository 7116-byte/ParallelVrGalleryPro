//! Right-eye rasterizer for V8/V9 External depth modes.
//!
//! Provides both ARGB+hole output (for JVM tests) and zero-copy strided RGBA8888 -> OpenCV BGR+holes
//! + BGR -> strided RGBA8888 output (eliminating intermediate IntArrays on Android).

use crate::projection::ProjectionError;

#[derive(Debug, Eq, PartialEq)]
pub struct ReferenceRaster {
    pub pixels: Vec<u32>,
    pub holes: Vec<u8>,
}

fn valid_shape(width: usize, height: usize, len: usize) -> bool {
    width > 0 && height > 0 && len <= i32::MAX as usize && width.checked_mul(height) == Some(len)
}

#[inline(always)]
fn sample_center(index: usize, depth_extent: usize, extent: usize) -> usize {
    let coordinate = (index as f64 + 0.5) * depth_extent as f64 / extent as f64;
    (coordinate.floor() as usize).min(depth_extent - 1)
}

/// Splat quantized, nearest-sampled depth from right to left (ARGB u32 layout).
pub fn project_reference(
    source: &[u32],
    width: usize,
    height: usize,
    depth: &[f32],
    depth_width: usize,
    depth_height: usize,
    scale: f32,
) -> Result<ReferenceRaster, ProjectionError> {
    if !valid_shape(width, height, source.len()) {
        return Err(ProjectionError::InvalidDimensions);
    }
    if !valid_shape(depth_width, depth_height, depth.len()) {
        return Err(ProjectionError::InvalidDepthLength);
    }
    if !(0.0..=100.0).contains(&scale) {
        return Err(ProjectionError::InvalidScale);
    }
    let mut pixels = Vec::new();
    let mut holes = Vec::new();
    if pixels.try_reserve_exact(source.len()).is_err()
        || holes.try_reserve_exact(source.len()).is_err()
    {
        return Err(ProjectionError::AllocationFailed);
    }
    pixels.resize(source.len(), 0xff00_0000);
    holes.resize(source.len(), 255);
    let factor = width as f64 * f64::from(scale) / 500.0 / 255.0;
    for y in 0..height {
        let row = y * width;
        let depth_row = sample_center(y, depth_height, height) * depth_width;
        for x in (0..width).rev() {
            let dx = sample_center(x, depth_width, width);
            let sample = depth[depth_row + dx];
            let sample = if sample.is_finite() { sample.clamp(0.0, 1.0) } else { 0.0 };
            let quantized = (sample * 255.0) as u32;
            let shift = ((f64::from(quantized) * factor) as usize).min(width - 1);
            let target = x + shift;
            if target < width {
                pixels[row + target] = source[row + x];
                holes[row + target] = 0;
            }
        }
    }
    Ok(ReferenceRaster { pixels, holes })
}

/// Zero-copy AndroidBitmap RGBA8888 -> OpenCV BGR (`width * height * 3`) + holes (`width * height`).
/// Eliminates both the source `IntArray` copy and the Kotlin ARGB->BGR conversion loop.
pub fn project_reference_rgba_to_bgr(
    source_rgba: &[u32],
    src_stride_px: usize,
    width: usize,
    height: usize,
    depth: &[f32],
    depth_width: usize,
    depth_height: usize,
    scale: f32,
    bgr_out: &mut [u8],
    holes_out: &mut [u8],
) -> Result<(), ProjectionError> {
    let pixel_count = width
        .checked_mul(height)
        .filter(|&n| n > 0 && n <= i32::MAX as usize)
        .ok_or(ProjectionError::InvalidDimensions)?;
    if src_stride_px < width {
        return Err(ProjectionError::InvalidDimensions);
    }
    let min_src = (height - 1)
        .checked_mul(src_stride_px)
        .and_then(|n| n.checked_add(width))
        .ok_or(ProjectionError::InvalidDimensions)?;
    if source_rgba.len() < min_src
        || bgr_out.len() != pixel_count.checked_mul(3).ok_or(ProjectionError::InvalidDimensions)?
        || holes_out.len() != pixel_count
    {
        return Err(ProjectionError::InvalidDimensions);
    }
    if !valid_shape(depth_width, depth_height, depth.len()) {
        return Err(ProjectionError::InvalidDepthLength);
    }
    if !(0.0..=100.0).contains(&scale) {
        return Err(ProjectionError::InvalidScale);
    }
    bgr_out.fill(0);
    holes_out.fill(255);
    let mut dx_lut = Vec::new();
    dx_lut
        .try_reserve_exact(width)
        .map_err(|_| ProjectionError::AllocationFailed)?;
    dx_lut.extend((0..width).map(|x| sample_center(x, depth_width, width) as u32));

    let factor = width as f64 * f64::from(scale) / 500.0 / 255.0;
    let bgr_base = bgr_out.as_mut_ptr() as usize;
    let holes_base = holes_out.as_mut_ptr() as usize;
    crate::parallel::for_each_row_stripe(width, height, |y_start, y_end| {
        for y in y_start..y_end {
            let src_row = &source_rgba[y * src_stride_px..y * src_stride_px + width];
            // SAFETY: Each stripe owns disjoint rows `y_start..y_end`.
            let bgr_row = unsafe {
                std::slice::from_raw_parts_mut((bgr_base as *mut u8).add(y * width * 3), width * 3)
            };
            let hole_row = unsafe {
                std::slice::from_raw_parts_mut((holes_base as *mut u8).add(y * width), width)
            };
            let depth_row = sample_center(y, depth_height, height) * depth_width;
            for x in (0..width).rev() {
                let sample = depth[depth_row + dx_lut[x] as usize];
                let sample = if sample.is_finite() { sample.clamp(0.0, 1.0) } else { 0.0 };
                let quantized = (sample * 255.0) as u32;
                let shift = ((f64::from(quantized) * factor) as usize).min(width - 1);
                let target = x + shift;
                if target < width {
                    let px = src_row[x];
                    let dst = target * 3;
                    bgr_row[dst] = (px >> 16) as u8;     // B
                    bgr_row[dst + 1] = (px >> 8) as u8;  // G
                    bgr_row[dst + 2] = px as u8;         // R
                    hole_row[target] = 0;
                }
            }
        }
    });
    Ok(())
}

/// Write OpenCV 3-byte BGR buffer directly into a strided RGBA8888 AndroidBitmap buffer.
pub fn bgr_to_rgba_strided(
    bgr: &[u8],
    dest_rgba: &mut [u32],
    dst_stride_px: usize,
    width: usize,
    height: usize,
) -> Result<(), ProjectionError> {
    let pixel_count = width
        .checked_mul(height)
        .filter(|&n| n > 0 && n <= i32::MAX as usize)
        .ok_or(ProjectionError::InvalidDimensions)?;
    if dst_stride_px < width
        || bgr.len() != pixel_count.checked_mul(3).ok_or(ProjectionError::InvalidDimensions)?
    {
        return Err(ProjectionError::InvalidDimensions);
    }
    let min_dst = (height - 1)
        .checked_mul(dst_stride_px)
        .and_then(|n| n.checked_add(width))
        .ok_or(ProjectionError::InvalidDimensions)?;
    if dest_rgba.len() < min_dst {
        return Err(ProjectionError::InvalidDimensions);
    }
    for y in 0..height {
        let src_row = &bgr[y * width * 3..(y + 1) * width * 3];
        let dst_row = &mut dest_rgba[y * dst_stride_px..y * dst_stride_px + width];
        for x in 0..width {
            let b = u32::from(src_row[x * 3]);
            let g = u32::from(src_row[x * 3 + 1]);
            let r = u32::from(src_row[x * 3 + 2]);
            dst_row[x] = 0xff00_0000 | (b << 16) | (g << 8) | r;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn swap_rb(argb: u32) -> u32 {
        (argb & 0xff00_ff00) | ((argb & 0x00ff_0000) >> 16) | ((argb & 0x0000_00ff) << 16)
    }

    #[test]
    fn reference_rgba_to_bgr_and_back_matches_argb_exactly() {
        let source_argb: Vec<u32> = (0..24)
            .map(|i| 0xff00_0000 | ((i as u32).wrapping_mul(0x2b95_f97f) & 0x00ff_ffff))
            .collect();
        let depth = [1.0, 0.02, 0.4, f32::NAN, f32::INFINITY, -1.0, 1.2, 0.5];
        let expected = project_reference(&source_argb, 12, 2, &depth, 4, 2, 100.0).unwrap();

        let stride = 15;
        let mut src_rgba = vec![0; stride * 2];
        for y in 0..2 {
            for x in 0..12 {
                src_rgba[y * stride + x] = swap_rb(source_argb[y * 12 + x]);
            }
        }
        let mut bgr = vec![0u8; 24 * 3];
        let mut holes = vec![0u8; 24];
        project_reference_rgba_to_bgr(&src_rgba, stride, 12, 2, &depth, 4, 2, 100.0, &mut bgr, &mut holes)
            .unwrap();
        assert_eq!(holes, expected.holes);

        let dst_stride = 14;
        let mut dst_rgba = vec![0; dst_stride * 2];
        bgr_to_rgba_strided(&bgr, &mut dst_rgba, dst_stride, 12, 2).unwrap();
        for y in 0..2 {
            for x in 0..12 {
                assert_eq!(swap_rb(dst_rgba[y * dst_stride + x]), expected.pixels[y * 12 + x]);
            }
        }
    }
}
