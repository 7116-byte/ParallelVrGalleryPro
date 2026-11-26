//! CPU stereo projection kernels for ComfyUI main-node modes (V10 legacy & V11 subpixel).
//!
//! Both functions support contiguous `u32` buffers (JVM ARGB `IntArray`) and strided `u32`
//! row buffers (zero-copy `AndroidBitmap` RGBA_8888 memory) with bit-identical results.

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ProjectionError {
    InvalidDimensions,
    InvalidDepthLength,
    InvalidScale,
    AllocationFailed,
}

fn validate_strided(
    src_len: usize,
    src_stride_px: usize,
    dst_len: usize,
    dst_stride_px: usize,
    width: usize,
    height: usize,
    depth_len: usize,
    scale: f32,
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
    if src_len < min_src || dst_len < min_dst {
        return Err(ProjectionError::InvalidDimensions);
    }
    if depth_len != width * height {
        return Err(ProjectionError::InvalidDepthLength);
    }
    if !(1.0..=100.0).contains(&scale) {
        return Err(ProjectionError::InvalidScale);
    }
    Ok(())
}

#[inline(always)]
fn clamped_depth(value: f32) -> f32 {
    if value.is_finite() {
        value.clamp(0.0, 1.0)
    } else {
        0.0
    }
}

fn reserve<T>(len: usize) -> Result<Vec<T>, ProjectionError> {
    let mut values = Vec::new();
    values
        .try_reserve_exact(len)
        .map_err(|_| ProjectionError::AllocationFailed)?;
    Ok(values)
}

fn filled<T: Copy>(len: usize, value: T) -> Result<Vec<T>, ProjectionError> {
    let mut values = reserve(len)?;
    values.resize(len, value);
    Ok(values)
}

#[inline(always)]
fn next_unwritten(unwritten: &mut [u32], start: usize) -> usize {
    let mut index = start;
    while unwritten[index] as usize != index {
        let parent = unwritten[index] as usize;
        unwritten[index] = unwritten[parent];
        index = unwritten[index] as usize;
    }
    index
}

/// V10 into caller-owned strided destination buffer (zero-copy AndroidBitmap friendly).
/// Works identically for ARGB and RGBA8888 words because whole 32-bit pixels are moved unchanged.
pub fn project_legacy_into(
    source: &[u32],
    src_stride_px: usize,
    dest: &mut [u32],
    dst_stride_px: usize,
    width: usize,
    height: usize,
    depth: &[f32],
    scale: f32,
) -> Result<(), ProjectionError> {
    validate_strided(
        source.len(),
        src_stride_px,
        dest.len(),
        dst_stride_px,
        width,
        height,
        depth.len(),
        scale,
    )?;
    let factor = f64::from(scale) / width as f64;
    let dst_base = dest.as_mut_ptr() as usize;
    crate::parallel::for_each_row_stripe(width, height, |y_start, y_end| {
        let mut unwritten = vec![0u32; width + 1];
        for y in y_start..y_end {
            let src_row = &source[y * src_stride_px..y * src_stride_px + width];
            // SAFETY: Each stripe owns disjoint rows `y_start..y_end` inside `dest`.
            let dst_row = unsafe {
                std::slice::from_raw_parts_mut((dst_base as *mut u32).add(y * dst_stride_px), width)
            };
            let depth_row = &depth[y * width..y * width + width];
            dst_row.copy_from_slice(src_row);
            for (column, parent) in unwritten.iter_mut().enumerate() {
                *parent = column as u32;
            }
            // Resolve the last writer first and visit each covered destination once.
            for x in (0..width).rev() {
                let quantized = (clamped_depth(depth_row[x]) * 255.0) as u32;
                let shift = (f64::from(quantized) * factor) as usize;
                let target = (x + shift).min(width - 1);
                let end = width.min(target + shift + 10);
                let mut destination = next_unwritten(&mut unwritten, target);
                while destination < end {
                    dst_row[destination] = src_row[x];
                    let next = next_unwritten(&mut unwritten, destination + 1);
                    unwritten[destination] = next as u32;
                    destination = next;
                }
            }
        }
    });
    Ok(())
}

/// V10 allocating variant for contiguous `IntArray` callers.
pub fn project_legacy(
    source: &[u32],
    width: usize,
    height: usize,
    depth: &[f32],
    scale: f32,
) -> Result<Vec<u32>, ProjectionError> {
    if width == 0 || height == 0 || width.checked_mul(height) != Some(source.len()) {
        return Err(ProjectionError::InvalidDimensions);
    }
    let mut result = filled(source.len(), 0u32)?;
    project_legacy_into(source, width, &mut result, width, width, height, depth, scale)?;
    Ok(result)
}

const DEPTH_TOLERANCE: f32 = 0.02;
const NO_NEIGHBOR: u32 = u32::MAX;

#[derive(Clone, Copy)]
struct Coverage {
    near: f32,
    far: f32,
    weight: f32,
    c2: f32, // bits 16..24 (R in ARGB, B in RGBA8888)
    c1: f32, // bits 8..16  (G in both)
    c0: f32, // bits 0..8   (B in ARGB, R in RGBA8888)
    background: u32,
}

impl Coverage {
    const EMPTY: Self = Self {
        near: -1.0,
        far: 2.0,
        weight: 0.0,
        c2: 0.0,
        c1: 0.0,
        c0: 0.0,
        background: 0,
    };

    #[inline(always)]
    fn add(&mut self, depth: f32, color: u32, coverage: f32) {
        if depth < self.far {
            self.far = depth;
            self.background = color;
        }
        if depth > self.near + DEPTH_TOLERANCE {
            self.weight = 0.0;
            self.c2 = 0.0;
            self.c1 = 0.0;
            self.c0 = 0.0;
        } else if depth < self.near - DEPTH_TOLERANCE {
            return;
        }
        self.near = self.near.max(depth);
        self.weight += coverage;
        self.c2 += component(color, 16) * coverage;
        self.c1 += component(color, 8) * coverage;
        self.c0 += component(color, 0) * coverage;
    }

    #[inline(always)]
    fn resolve(self, neighbor: Option<Self>, source: u32) -> u32 {
        if self.weight <= 0.0 {
            return neighbor.map_or(source, |pixel| pixel.background);
        }
        let back = neighbor
            .filter(|pixel| pixel.far < self.far)
            .unwrap_or(self);
        let alpha = if back.far < self.near - DEPTH_TOLERANCE {
            self.weight.min(1.0)
        } else {
            1.0
        };
        let channel = |sum: f32, shift: u32| {
            let foreground = sum / self.weight * alpha;
            let background = component(back.background, shift) * (1.0 - alpha);
            ((foreground + background + 0.5) as u32).min(255)
        };
        0xff00_0000
            | (channel(self.c2, 16) << 16)
            | (channel(self.c1, 8) << 8)
            | channel(self.c0, 0)
    }
}

#[inline(always)]
fn component(color: u32, shift: u32) -> f32 {
    ((color >> shift) & 255) as f32
}

#[inline(always)]
fn farther_neighbor(
    row: &[Coverage],
    target: usize,
    left: u32,
    right: u32,
    max_search: usize,
) -> Option<Coverage> {
    let left = (left != NO_NEIGHBOR)
        .then_some(left as usize)
        .filter(|&index| target - index <= max_search);
    let right = (right != NO_NEIGHBOR)
        .then_some(right as usize)
        .filter(|&index| index - target <= max_search);
    match (left, right) {
        (None, None) => None,
        (Some(index), None) | (None, Some(index)) => Some(row[index]),
        (Some(left), Some(right)) => {
            let a = row[left];
            let b = row[right];
            if (a.far - b.far).abs() > DEPTH_TOLERANCE {
                if a.far < b.far { Some(a) } else { Some(b) }
            } else if target - left <= right - target {
                Some(a)
            } else {
                Some(b)
            }
        }
    }
}

/// V11 into caller-owned strided destination buffer (zero-copy AndroidBitmap friendly).
/// Symmetric across channels 0/1/2 with opaque alpha 0xff, so bit-identical on both ARGB and
/// opaque RGBA8888 buffers.
pub fn project_subpixel_into(
    source: &[u32],
    src_stride_px: usize,
    dest: &mut [u32],
    dst_stride_px: usize,
    width: usize,
    height: usize,
    depth: &[f32],
    scale: f32,
) -> Result<(), ProjectionError> {
    validate_strided(
        source.len(),
        src_stride_px,
        dest.len(),
        dst_stride_px,
        width,
        height,
        depth.len(),
        scale,
    )?;
    let factor = 255.0 * f64::from(scale) / width as f64;
    let max_search = width.min(factor.ceil() as usize + 12);
    let dst_base = dest.as_mut_ptr() as usize;
    crate::parallel::for_each_row_stripe(width, height, |y_start, y_end| {
        let mut row = vec![Coverage::EMPTY; width];
        let mut left = vec![NO_NEIGHBOR; width];
        for y in y_start..y_end {
            row.fill(Coverage::EMPTY);
            let src_row = &source[y * src_stride_px..y * src_stride_px + width];
            // SAFETY: Each stripe owns disjoint rows `y_start..y_end` inside `dest`.
            let dst_row = unsafe {
                std::slice::from_raw_parts_mut((dst_base as *mut u32).add(y * dst_stride_px), width)
            };
            let depth_row = &depth[y * width..y * width + width];
            for x in 0..width {
                let d = clamped_depth(depth_row[x]);
                let position = x as f64 + f64::from(d) * factor;
                let first = position.floor() as usize;
                let fraction = (position - first as f64) as f32;
                let color = src_row[x];
                if first < width {
                    let w0 = 1.0 - fraction;
                    if w0 > 0.0 {
                        row[first].add(d, color, w0);
                    }
                }
                let second = first + 1;
                if second < width && fraction > 0.0 {
                    row[second].add(d, color, fraction);
                }
            }
            let mut nearest = NO_NEIGHBOR;
            for x in 0..width {
                left[x] = nearest;
                if row[x].weight > 0.0 {
                    nearest = x as u32;
                }
            }
            nearest = NO_NEIGHBOR;
            for x in (0..width).rev() {
                let neighbor = farther_neighbor(&row, x, left[x], nearest, max_search);
                dst_row[x] = row[x].resolve(neighbor, src_row[x]);
                if row[x].weight > 0.0 {
                    nearest = x as u32;
                }
            }
        }
    });
    Ok(())
}

/// V11 allocating variant for contiguous `IntArray` callers.
pub fn project_subpixel(
    source: &[u32],
    width: usize,
    height: usize,
    depth: &[f32],
    scale: f32,
) -> Result<Vec<u32>, ProjectionError> {
    if width == 0 || height == 0 || width.checked_mul(height) != Some(source.len()) {
        return Err(ProjectionError::InvalidDimensions);
    }
    let mut result = filled(source.len(), 0u32)?;
    project_subpixel_into(source, width, &mut result, width, width, height, depth, scale)?;
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn source_pixel(index: usize) -> u32 {
        (index as u32)
            .wrapping_mul(0x2b95_f97f)
            .wrapping_add(0x1729_6abb)
    }

    fn swap_rb(argb: u32) -> u32 {
        (argb & 0xff00_ff00) | ((argb & 0x00ff_0000) >> 16) | ((argb & 0x0000_00ff) << 16)
    }

    #[test]
    fn exact_kotlin_argb_vectors_and_strided_rgba_equivalence() {
        let source: Vec<_> = (0..16).map(source_pixel).collect();
        let depth = [
            0.0, 0.02, 0.04, 0.125, 0.25, 0.031, 0.0, f32::NAN, -1.0, f32::INFINITY, 0.14, 0.19,
            0.031, 0.06, 1.0, 0.022,
        ];
        let legacy = [
            0x17296abb, 0x42bf643a, 0x42bf643a, 0x6e555db9, 0x6e555db9, 0xf1174a36, 0x1cad43b5,
            0x48433d34, 0x73d936b3, 0x9f6f3032, 0x9f6f3032, 0x9f6f3032, 0x22311caf, 0x22311caf,
            0x4dc7162e, 0xa4f3092c,
        ];
        let subpixel = [
            0xff296abb, 0xffbf643a, 0xffbf643a, 0xff555db9, 0xff555db9, 0xffab43b3, 0xffae43b3,
            0xffe85738, 0xffd936b3, 0xff6f3032, 0x9f6f3032, 0x9f6f3032, 0xff6e3033, 0xff311caf,
            0xff1923b0, 0xff85186a,
        ];
        assert_eq!(project_legacy(&source, 8, 2, &depth, 1.0).unwrap(), legacy);
        assert_eq!(project_subpixel(&source, 8, 2, &depth, 1.0).unwrap(), subpixel);

        // Verify strided RGBA8888 matches byte-for-byte after R/B swap and ignores padding columns.
        let src_stride = 11;
        let dst_stride = 13;
        let mut src_rgba = vec![0xdead_beef; src_stride * 2];
        for y in 0..2 {
            for x in 0..8 {
                src_rgba[y * src_stride + x] = swap_rb(source[y * 8 + x]);
            }
        }
        let mut dst_legacy = vec![0x1111_1111; dst_stride * 2];
        let mut dst_subpixel = vec![0x2222_2222; dst_stride * 2];
        project_legacy_into(&src_rgba, src_stride, &mut dst_legacy, dst_stride, 8, 2, &depth, 1.0)
            .unwrap();
        project_subpixel_into(
            &src_rgba,
            src_stride,
            &mut dst_subpixel,
            dst_stride,
            8,
            2,
            &depth,
            1.0,
        )
        .unwrap();
        for y in 0..2 {
            for x in 0..8 {
                assert_eq!(swap_rb(dst_legacy[y * dst_stride + x]), legacy[y * 8 + x]);
                assert_eq!(swap_rb(dst_subpixel[y * dst_stride + x]), subpixel[y * 8 + x]);
            }
            for x in 8..dst_stride {
                assert_eq!(dst_legacy[y * dst_stride + x], 0x1111_1111);
                assert_eq!(dst_subpixel[y * dst_stride + x], 0x2222_2222);
            }
        }
    }

    #[test]
    fn subpixel_half_coverage_keeps_far_background_outside_foreground() {
        let background = 0xff00_00ff;
        let foreground = 0xffff_0000;
        let mut source = vec![background; 255];
        source[10] = foreground;
        let mut depth = vec![0.0; source.len()];
        depth[10] = 1.0;
        assert_eq!(project_subpixel(&source, 255, 1, &depth, 1.0).unwrap()[11], foreground);
        depth[10] = 0.5;
        let result = project_subpixel(&source, 255, 1, &depth, 1.0).unwrap();
        assert_eq!(result[11], 0xff80_0080);
        assert_eq!(result[12], background);
    }

    #[test]
    fn invalid_dimensions_depth_length_and_scale_are_rejected() {
        for project in [project_legacy, project_subpixel] {
            assert_eq!(project(&[0], 0, 1, &[0.0], 1.0), Err(ProjectionError::InvalidDimensions));
            assert_eq!(
                project(&[0], usize::MAX, 2, &[0.0], 1.0),
                Err(ProjectionError::InvalidDimensions)
            );
            assert_eq!(project(&[0], 1, 1, &[], 1.0), Err(ProjectionError::InvalidDepthLength));
            for invalid in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY, 0.999, 100.001] {
                assert_eq!(project(&[0], 1, 1, &[0.0], invalid), Err(ProjectionError::InvalidScale));
            }
        }
    }
}
