//! Depth normalization, rotation, resizing, temporal smoothing, and post-processing.

use crate::legacy_stereo::{PhoneDepthFilter, filter_phone_depth};
use crate::projection::ProjectionError;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ModelDepthError {
    NonFinite,
    InsufficientRange,
}

pub fn normalize_model_depth(values: &mut [f32], strict: bool) -> Result<f32, ModelDepthError> {
    let mut minimum = f32::INFINITY;
    let mut maximum = f32::NEG_INFINITY;
    for value in values.iter_mut() {
        if !value.is_finite() {
            if strict {
                return Err(ModelDepthError::NonFinite);
            }
            *value = 0.0;
        }
        // Match Java Math.min/max for signed zero as well as ordinary samples.
        if *value < minimum || (*value == minimum && value.is_sign_negative()) {
            minimum = *value;
        }
        if *value > maximum || (*value == maximum && value.is_sign_positive()) {
            maximum = *value;
        }
    }
    let raw_range = maximum - minimum;
    if strict && (!raw_range.is_finite() || raw_range <= 0.0001) {
        return Err(ModelDepthError::InsufficientRange);
    }
    let divisor = if raw_range.is_finite() {
        raw_range.max(0.0001)
    } else {
        0.0001
    };
    for value in values {
        *value = (*value - minimum) / divisor;
    }
    Ok(raw_range)
}

/// Tiled 32x32 counter-clockwise 90-degree rotation for QNN dual-portrait landscape depth maps.
pub fn rotate_ccw_90(values: &[f32], width: usize, height: usize) -> Result<Vec<f32>, ProjectionError> {
    let total = count(width, height)?;
    if total != values.len() {
        return Err(ProjectionError::InvalidDepthLength);
    }
    let mut turned = allocate(total)?;
    turned.resize(total, 0.0);
    const TILE: usize = 32;
    for top in (0..width).step_by(TILE) {
        let top_end = (top + TILE).min(width);
        for left in (0..height).step_by(TILE) {
            let length = TILE.min(height - left);
            for y in top..top_end {
                let mut from = left * width + (width - 1 - y);
                let mut to = y * height + left;
                for _ in 0..length {
                    turned[to] = values[from];
                    to += 1;
                    from += width;
                }
            }
        }
    }
    Ok(turned)
}

/// Extract visible QNN content (optionally rotating CCW 90°) and strictly normalize in one pass.
pub fn extract_and_normalize_qnn(
    tensor: &[f32],
    tensor_width: usize,
    tensor_height: usize,
    content_width: usize,
    content_height: usize,
    left: usize,
    top: usize,
    rotate_ccw: bool,
) -> Result<Vec<f32>, Result<ModelDepthError, ProjectionError>> {
    let total_tensor = count(tensor_width, tensor_height).map_err(Err)?;
    if tensor.len() < total_tensor
        || content_width == 0
        || content_height == 0
        || left.checked_add(content_width).is_none_or(|e| e > tensor_width)
        || top.checked_add(content_height).is_none_or(|e| e > tensor_height)
    {
        return Err(Err(ProjectionError::InvalidDimensions));
    }
    let mut values = if rotate_ccw {
        if content_width != tensor_width || content_height != tensor_height {
            return Err(Err(ProjectionError::InvalidDimensions));
        }
        rotate_ccw_90(&tensor[..total_tensor], tensor_width, tensor_height).map_err(Err)?
    } else if content_width == tensor_width && content_height == tensor_height && left == 0 && top == 0 {
        let mut out = allocate(total_tensor).map_err(Err)?;
        out.extend_from_slice(&tensor[..total_tensor]);
        out
    } else {
        let mut out = allocate(content_width * content_height).map_err(Err)?;
        for y in 0..content_height {
            let row_start = (y + top) * tensor_width + left;
            out.extend_from_slice(&tensor[row_start..row_start + content_width]);
        }
        out
    };
    normalize_model_depth(&mut values, true).map_err(Ok)?;
    Ok(values)
}

pub fn disparity_span(values: &[f32]) -> Result<f32, ProjectionError> {
    let count = values.len().min(32_768);
    if count == 0 {
        return Ok(0.0);
    }
    let mut sample = allocate(count)?;
    for index in 0..count {
        let source = (index as u64 * values.len() as u64 / count as u64) as usize;
        let value = values[source];
        sample.push(if value.is_finite() { value.clamp(0.0, 1.0) } else { 0.0 });
    }
    let last = (count - 1) as f64;
    let (_, lower, _) = sample.select_nth_unstable_by((last * 0.1) as usize, f32::total_cmp);
    let lower = *lower;
    let (_, upper, _) = sample.select_nth_unstable_by((last * 0.9) as usize, f32::total_cmp);
    Ok(*upper - lower)
}

#[derive(Clone, Copy)]
pub enum Interpolation {
    Nearest,
    Linear,
    Cubic,
}

impl Interpolation {
    pub fn from_code(code: i32) -> Option<Self> {
        match code {
            0 => Some(Self::Nearest),
            1 => Some(Self::Linear),
            2 => Some(Self::Cubic),
            _ => None,
        }
    }

    fn taps(self) -> usize {
        match self {
            Self::Nearest => 1,
            Self::Linear => 2,
            Self::Cubic => 4,
        }
    }
}

pub fn allocate<T>(length: usize) -> Result<Vec<T>, ProjectionError> {
    let mut samples = Vec::new();
    samples
        .try_reserve_exact(length)
        .map_err(|_| ProjectionError::AllocationFailed)?;
    Ok(samples)
}

fn axis(source: usize, target: usize, method: Interpolation) -> Result<Vec<[(usize, f32); 4]>, ProjectionError> {
    let mut axis = allocate(target)?;
    axis.extend((0..target).map(|i| {
        let position = if target == 1 {
            (source - 1) as f64 / 2.0
        } else {
            i as f64 * (source - 1) as f64 / (target - 1) as f64
        };
        let base = position.floor() as i64;
        let t = (position - base as f64) as f32;
        let sample = |i: i64| i.clamp(0, (source - 1) as i64) as usize;
        match method {
            Interpolation::Nearest => [((position + 0.5).floor() as usize, 1.0), (0, 0.0), (0, 0.0), (0, 0.0)],
            Interpolation::Linear => [(sample(base), 1.0 - t), (sample(base + 1), t), (0, 0.0), (0, 0.0)],
            Interpolation::Cubic => [
                (sample(base - 1), -0.5 * t + t * t - 0.5 * t * t * t),
                (sample(base), 1.0 - 2.5 * t * t + 1.5 * t * t * t),
                (sample(base + 1), 0.5 * t + 2.0 * t * t - 1.5 * t * t * t),
                (sample(base + 2), -0.5 * t * t + 0.5 * t * t * t),
            ],
        }
    }));
    Ok(axis)
}

fn count(width: usize, height: usize) -> Result<usize, ProjectionError> {
    width
        .checked_mul(height)
        .filter(|count| *count > 0 && *count <= i32::MAX as usize)
        .ok_or(ProjectionError::InvalidDimensions)
}

/// Endpoint-aligned float resampling.
pub fn resize(
    source: &[f32],
    sw: usize,
    sh: usize,
    dw: usize,
    dh: usize,
    method: Interpolation,
) -> Result<Vec<f32>, ProjectionError> {
    if count(sw, sh)? != source.len() {
        return Err(ProjectionError::InvalidDepthLength);
    }
    let count_out = count(dw, dh)?;
    let horizontal_count = count(dw, sh)?;
    let xs = axis(sw, dw, method)?;
    let ys = axis(sh, dh, method)?;
    let taps = method.taps();
    let mut horizontal = allocate(horizontal_count)?;
    for row in source.chunks_exact(sw) {
        horizontal.extend(xs.iter().map(|x| {
            x[..taps]
                .iter()
                .fold(0.0_f32, |value, (index, weight)| value + row[*index] * weight)
        }));
    }
    let mut result = allocate(count_out)?;
    for y in ys {
        result.extend((0..dw).map(|x| {
            y[..taps]
                .iter()
                .fold(0.0_f32, |value, (index, weight)| value + horizontal[*index * dw + x] * weight)
                .clamp(0.0, 1.0)
        }));
    }
    Ok(result)
}

pub fn target_size(width: i32, height: i32, long_edge: i32, max_pixels: i64) -> Option<(i32, i32)> {
    if width <= 0 || height <= 0 || max_pixels <= 0 {
        return None;
    }
    let requested = if long_edge == 0 {
        1.0
    } else {
        (f64::from(long_edge) / f64::from(width.max(height))).min(1.0)
    };
    let original_pixels = i64::from(width) * i64::from(height);
    let scale = requested.min(1.0_f64.min((max_pixels as f64 / original_pixels as f64).sqrt()));
    let width = (f64::from(width) * scale).floor().max(1.0) as i32;
    let height = (f64::from(height) * scale).floor().max(1.0) as i32;
    Some((
        (i64::from(width).min(max_pixels / i64::from(height))).max(1) as i32,
        height.min(max_pixels.min(i64::from(i32::MAX)) as i32),
    ))
}

/// Returns false on a scene cut (leaves `current` unchanged).
pub fn smooth_temporal(
    current: &mut [f32],
    previous: &[f32],
    previous_weight: f32,
    scene_cut: f32,
    motion: f32,
) -> bool {
    if current.is_empty() || current.len() != previous.len() {
        return false;
    }
    let (sum, count) = current
        .iter()
        .zip(previous)
        .step_by(16)
        .fold((0.0_f32, 0), |(sum, count), (a, b)| (sum + (a - b).abs(), count + 1));
    if sum / count as f32 > scene_cut {
        return false;
    }
    for (current, previous) in current.iter_mut().zip(previous) {
        let ratio = (1.0 - (*current - previous).abs() / motion).clamp(0.0, 1.0);
        let old_weight = previous_weight * ratio * ratio;
        *current = previous * old_weight + *current * (1.0 - old_weight);
    }
    true
}

pub fn map_direction(samples: &mut [f32], invert: bool, clamp: bool) {
    for value in samples {
        if invert {
            *value = 1.0 - *value;
        }
        if clamp {
            *value = value.clamp(0.0, 1.0);
        }
    }
}

pub struct PostProcess {
    pub target: Option<(usize, usize)>,
    pub method: Interpolation,
    pub resize_first: bool,
    pub mode: i32,
    pub invert: bool,
    pub diameter: i32,
}

impl PostProcess {
    pub fn apply(&self, source: Vec<f32>, width: usize, height: usize) -> Result<Vec<f32>, ProjectionError> {
        let filter = |mut depth: Vec<f32>, width, height| {
            if self.mode <= 1 {
                let filter = if self.mode == 0 {
                    PhoneDepthFilter::LegacyV6
                } else {
                    PhoneDepthFilter::CenteredV7
                };
                filter_phone_depth(&depth, width, height, self.diameter, self.invert, filter)
            } else {
                map_direction(&mut depth, if self.mode >= 4 { !self.invert } else { self.invert }, false);
                Ok(depth)
            }
        };
        let Some((dw, dh)) = self.target.filter(|target| *target != (width, height)) else {
            return filter(source, width, height);
        };
        if self.resize_first {
            let scaled = resize(&source, width, height, dw, dh, self.method)?;
            drop(source);
            filter(scaled, dw, dh)
        } else {
            let filtered = filter(source, width, height)?;
            resize(&filtered, width, height, dw, dh, self.method)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn model_normalization_and_rotation_preserve_contracts() {
        let mut values = [f32::NAN, -2.0, 2.0, f32::INFINITY];
        assert_eq!(normalize_model_depth(&mut values, true), Err(ModelDepthError::NonFinite));
        assert_eq!(normalize_model_depth(&mut values, false), Ok(4.0));
        assert_eq!(values, [0.5, 0.0, 1.0, 0.5]);
        assert_eq!(normalize_model_depth(&mut [1.0, 1.0], true), Err(ModelDepthError::InsufficientRange));

        // 3x2 rotated CCW 90 -> 2x3: (x, y) in old maps to (y, width - 1 - x) in new
        let grid = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0];
        let rotated = rotate_ccw_90(&grid, 3, 2).unwrap();
        assert_eq!(rotated, [3.0, 6.0, 2.0, 5.0, 1.0, 4.0]);

        let extracted = extract_and_normalize_qnn(&grid, 3, 2, 3, 2, 0, 0, true).unwrap();
        assert_eq!(extracted, [0.4, 1.0, 0.2, 0.8, 0.0, 0.6]);
    }

    #[test]
    fn disparity_span_interpolation_and_temporal() {
        let depth = [0.0, 1.0, 1.0, 0.0];
        assert_eq!(resize(&depth, 2, 2, 1, 1, Interpolation::Linear).unwrap(), [0.5]);
        assert_eq!(
            resize(&depth, 2, 2, 3, 3, Interpolation::Cubic).unwrap(),
            [0.0, 0.5, 1.0, 0.5, 0.5, 0.5, 1.0, 0.5, 0.0]
        );
        assert_eq!(target_size(1920, 1080, 0, 1), Some((1, 1)));
        assert_eq!(disparity_span(&[f32::NAN, 0.3, 0.8, f32::INFINITY]), Ok(0.3));
        let prior = [0.4; 17];
        let mut frame = [0.41; 17];
        assert!(smooth_temporal(&mut frame, &prior, 0.62, 0.20, 0.08));
        let mut cut = [0.9; 17];
        assert!(!smooth_temporal(&mut cut, &frame, 0.62, 0.20, 0.08));
    }
}
