//! Legacy phone stereo used by V6 and V7.
//!
//! Supports both contiguous ARGB buffers and strided zero-copy AndroidBitmap RGBA8888 buffers.

use crate::projection::ProjectionError;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PhoneDepthFilter {
    LegacyV6,
    CenteredV7,
}

fn valid_shape(width: usize, height: usize, len: usize) -> bool {
    width > 0 && height > 0 && len <= i32::MAX as usize && width.checked_mul(height) == Some(len)
}

fn reserve<T>(len: usize) -> Result<Vec<T>, ProjectionError> {
    let mut result = Vec::new();
    result
        .try_reserve_exact(len)
        .map_err(|_| ProjectionError::AllocationFailed)?;
    Ok(result)
}

#[inline(always)]
fn endpoint_sample(index: usize, extent: usize, depth_extent: usize) -> usize {
    (index as u64 * (depth_extent - 1) as u64 / (extent - 1).max(1) as u64) as usize
}

/// Project the left eye into a strided caller-owned destination buffer (zero-copy AndroidBitmap).
/// Works bit-identically on both ARGB and RGBA8888 words because whole 32-bit pixels are copied.
pub fn project_phone_stereo_into(
    source: &[u32],
    src_stride_px: usize,
    dest: &mut [u32],
    dst_stride_px: usize,
    width: usize,
    height: usize,
    depth: &[f32],
    depth_width: usize,
    depth_height: usize,
    scale: f32,
    fill_radius: i32,
) -> Result<(), ProjectionError> {
    if width == 0
        || height == 0
        || src_stride_px < width
        || dst_stride_px < width
        || width.checked_mul(height).is_none_or(|n| n > i32::MAX as usize)
    {
        return Err(ProjectionError::InvalidDimensions);
    }
    let min_src = (height - 1)
        .checked_mul(src_stride_px)
        .and_then(|n| n.checked_add(width))
        .ok_or(ProjectionError::InvalidDimensions)?;
    let min_dst = (height - 1)
        .checked_mul(dst_stride_px)
        .and_then(|n| n.checked_add(width))
        .ok_or(ProjectionError::InvalidDimensions)?;
    if source.len() < min_src || dest.len() < min_dst {
        return Err(ProjectionError::InvalidDimensions);
    }
    if !valid_shape(depth_width, depth_height, depth.len()) {
        return Err(ProjectionError::InvalidDepthLength);
    }
    let mut dx = reserve(width)?;
    dx.extend((0..width).map(|x| endpoint_sample(x, width, depth_width) as u32));
    let fill = fill_radius.clamp(1, 32);
    let depth_scaling = scale / width as f32;
    let last_x = (width - 1) as i32;
    let dst_base = dest.as_mut_ptr() as usize;
    crate::parallel::for_each_row_stripe(width, height, |y_start, y_end| {
        for y in y_start..y_end {
            let src_row = &source[y * src_stride_px..y * src_stride_px + width];
            // SAFETY: Each stripe owns disjoint rows `y_start..y_end` inside `dest`.
            let dst_row = unsafe {
                std::slice::from_raw_parts_mut((dst_base as *mut u32).add(y * dst_stride_px), width)
            };
            dst_row.copy_from_slice(src_row);
            let depth_base = endpoint_sample(y, height, depth_height) * depth_width;
            for x in (0..width).rev() {
                let sample = depth[depth_base + dx[x] as usize];
                // Keep the JVM float operations and truncating/saturating conversion.
                let shift = ((sample.clamp(0.0, 1.0) * 255.0 * depth_scaling) as i32).clamp(0, last_x);
                let color = src_row[x];
                for offset in 0..fill {
                    let target = (x as i32)
                        .wrapping_add(shift)
                        .wrapping_add(offset)
                        .clamp(0, last_x);
                    dst_row[target as usize] = color;
                }
            }
        }
    });
    Ok(())
}

/// Project the left eye into a newly allocated contiguous buffer.
pub fn project_phone_stereo(
    source: &[u32],
    width: usize,
    height: usize,
    depth: &[f32],
    depth_width: usize,
    depth_height: usize,
    scale: f32,
    fill_radius: i32,
) -> Result<Vec<u32>, ProjectionError> {
    if !valid_shape(width, height, source.len()) {
        return Err(ProjectionError::InvalidDimensions);
    }
    let mut left = reserve(source.len())?;
    left.resize(source.len(), 0);
    project_phone_stereo_into(
        source,
        width,
        &mut left,
        width,
        width,
        height,
        depth,
        depth_width,
        depth_height,
        scale,
        fill_radius,
    )?;
    Ok(left)
}

#[derive(Clone, Copy)]
struct Axis {
    length: usize,
    lines: usize,
    step: usize,
    line_step: usize,
}

#[inline(always)]
fn invert_if_requested(value: f32, invert: bool) -> f32 {
    if invert { 1.0 - value } else { value }
}

fn legacy_pass(input: &[f32], output: &mut [f32], axis: Axis, radius: usize, invert: bool) {
    for line in 0..axis.lines {
        let start = line * axis.line_step;
        let mut sum = 0.0_f32;
        for index in 0..axis.length {
            let at = start + index * axis.step;
            sum += input[at];
            if index > radius {
                sum -= input[start + (index - radius - 1) * axis.step];
            }
            // The old pass sums only up to the current coordinate but divides by
            // the clipped centered-window length, including at the border.
            let count = (index + radius + 1).min(axis.length) - index.saturating_sub(radius);
            output[at] = invert_if_requested(sum / count as f32, invert);
        }
    }
}

fn centered_pass(input: &[f32], output: &mut [f32], axis: Axis, radius: usize, invert: bool) {
    for line in 0..axis.lines {
        let start = line * axis.line_step;
        let mut sum = 0.0_f64;
        for index in 0..=radius.min(axis.length - 1) {
            sum += f64::from(input[start + index * axis.step]);
        }
        for index in 0..axis.length {
            let at = start + index * axis.step;
            let count = (index + radius).min(axis.length - 1) - index.saturating_sub(radius) + 1;
            output[at] = invert_if_requested((sum / count as f64) as f32, invert);
            if index >= radius {
                sum -= f64::from(input[start + (index - radius) * axis.step]);
            }
            if index + radius + 1 < axis.length {
                sum += f64::from(input[start + (index + radius + 1) * axis.step]);
            }
        }
    }
}

/// Reproduce the separable V6/V7 means and their original inversion ordering.
pub fn filter_phone_depth(
    depth: &[f32],
    width: usize,
    height: usize,
    diameter: i32,
    invert: bool,
    mode: PhoneDepthFilter,
) -> Result<Vec<f32>, ProjectionError> {
    if !valid_shape(width, height, depth.len()) {
        return Err(ProjectionError::InvalidDepthLength);
    }
    let radius = (diameter.max(0) / 2) as usize;
    let mut result = reserve(depth.len())?;
    if diameter <= 0 || (mode == PhoneDepthFilter::CenteredV7 && radius == 0) {
        result.extend(depth.iter().map(|&value| invert_if_requested(value, invert)));
        return Ok(result);
    }
    let mut horizontal = reserve(depth.len())?;
    horizontal.resize(depth.len(), 0.0);
    result.resize(depth.len(), 0.0);
    let rows = Axis {
        length: width,
        lines: height,
        step: 1,
        line_step: width,
    };
    let columns = Axis {
        length: height,
        lines: width,
        step: width,
        line_step: 1,
    };
    let pass = match mode {
        PhoneDepthFilter::LegacyV6 => legacy_pass,
        PhoneDepthFilter::CenteredV7 => centered_pass,
    };
    pass(depth, &mut horizontal, rows, radius, false);
    pass(&horizontal, &mut result, columns, radius, invert);
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn color(index: usize) -> u32 {
        (index as u32)
            .wrapping_mul(0x2b95_f97f)
            .wrapping_add(0x1729_6abb)
    }

    fn depth(index: usize) -> f32 {
        let half = 0.5_f32.to_bits();
        match index % 17 {
            0 => f32::NAN,
            1 => f32::INFINITY,
            2 => f32::NEG_INFINITY,
            3 => -1.2,
            4 => 1.2,
            5 => -0.0,
            6 => 0.0,
            7 => f32::from_bits(1),
            8 => 1e-8,
            9 => f32::from_bits(half - 1),
            10 => 0.5,
            11 => f32::from_bits(half + 1),
            12 => 1.0,
            _ => {
                let index = index as u32;
                let bits = index.wrapping_mul(73_856_093)
                    ^ index.wrapping_add(23).wrapping_mul(19_349_663);
                (bits >> 8) as f32 / 16_777_216.0
            }
        }
    }

    fn fingerprint(words: impl IntoIterator<Item = u32>) -> u64 {
        words.into_iter().fold(0xcbf2_9ce4_8422_2325, |hash, word| {
            (hash ^ u64::from(word)).wrapping_mul(0x0100_0000_01b3)
        })
    }

    #[test]
    fn exact_kotlin_rows_and_strided_phone_stereo() {
        let source: Vec<_> = (0..14).map(color).collect();
        let input = [1.0, 0.6, 0.0, f32::NAN, f32::INFINITY, f32::NEG_INFINITY];
        let expected = [
            0x17296abb, 0x42bf643a, 0x6e555db9, 0x17296abb, 0x17296abb, 0x42bf643a, 0x6e555db9,
            0x48433d34, 0x48433d34, 0x73d936b3, 0x9f6f3032, 0xf69b2330, 0x22311caf, 0xcb0529b1,
        ];
        assert_eq!(project_phone_stereo(&source, 7, 2, &input, 3, 2, 0.1, 2).unwrap(), expected);

        let mut strided_src = vec![0xaaaa_aaaa; 10 * 2];
        for y in 0..2 {
            strided_src[y * 10..y * 10 + 7].copy_from_slice(&source[y * 7..y * 7 + 7]);
        }
        let mut strided_dst = vec![0x5555_5555; 9 * 2];
        project_phone_stereo_into(&strided_src, 10, &mut strided_dst, 9, 7, 2, &input, 3, 2, 0.1, 2)
            .unwrap();
        for y in 0..2 {
            assert_eq!(&strided_dst[y * 9..y * 9 + 7], &expected[y * 7..y * 7 + 7]);
            assert_eq!(&strided_dst[y * 9 + 7..y * 9 + 9], &[0x5555_5555; 2]);
        }
    }

    #[test]
    fn matches_kotlin_filter_fingerprints() {
        let cases = [
            (1, 1, 0, false, 0xaf63bc730601b7df, 0xaf63bc730601b7df),
            (4, 3, 0, true, 0x2005d9642c8be189, 0x2005d9642c8be189),
            (4, 3, 1, false, 0xf64fd5f6e1906395, 0x3b1069bb70502649),
            (7, 5, 3, false, 0x17ff946d8f55c04d, 0x12f4c2d9bfe1bd23),
            (31, 23, 8, true, 0x12c119be824c5f13, 0x38b81a5efd61d361),
        ];
        for (width, height, diameter, invert, v6, v7) in cases {
            let input: Vec<_> = (0..width * height)
                .map(|index| {
                    let value = depth(index + 5);
                    if value.is_finite() {
                        value
                    } else {
                        0.3 + (index % 7) as f32 * 0.03
                    }
                })
                .collect();
            for (mode, expected) in [(PhoneDepthFilter::LegacyV6, v6), (PhoneDepthFilter::CenteredV7, v7)] {
                let output = filter_phone_depth(&input, width, height, diameter, invert, mode).unwrap();
                assert_eq!(fingerprint(output.into_iter().map(f32::to_bits)), expected);
            }
        }
    }
}
