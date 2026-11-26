//! Zero-copy RGB tensor packing (QNN NCHW ImageNet-normalized & TFLite NHWC [0, 1]).

use crate::projection::ProjectionError;

const IMAGENET_MEAN: [f32; 3] = [0.485, 0.456, 0.406];
const IMAGENET_STD: [f32; 3] = [0.229, 0.224, 0.225];

/// Pack a strided AndroidBitmap RGBA8888 buffer directly into QNN's NCHW Direct FloatBuffer
/// with border-clamping padding when `content` is smaller than `tensor`.
/// Uses the exact same float arithmetic `(byte / 255f - mean) / std` as Kotlin's lookup table.
pub fn pack_qnn_nchw_from_rgba(
    source_rgba: &[u32],
    src_stride_px: usize,
    content_width: usize,
    content_height: usize,
    tensor_width: usize,
    tensor_height: usize,
    left: usize,
    top: usize,
    out_nchw: &mut [f32],
) -> Result<(), ProjectionError> {
    let count = tensor_width
        .checked_mul(tensor_height)
        .filter(|&n| n > 0 && n <= i32::MAX as usize)
        .ok_or(ProjectionError::InvalidDimensions)?;
    if content_width == 0
        || content_height == 0
        || src_stride_px < content_width
        || left.checked_add(content_width).is_none_or(|e| e > tensor_width)
        || top.checked_add(content_height).is_none_or(|e| e > tensor_height)
        || out_nchw.len() < count * 3
    {
        return Err(ProjectionError::InvalidDimensions);
    }
    let min_src = (content_height - 1)
        .checked_mul(src_stride_px)
        .and_then(|n| n.checked_add(content_width))
        .ok_or(ProjectionError::InvalidDimensions)?;
    if source_rgba.len() < min_src {
        return Err(ProjectionError::InvalidDimensions);
    }

    let mut lut_r = [0.0f32; 256];
    let mut lut_g = [0.0f32; 256];
    let mut lut_b = [0.0f32; 256];
    for i in 0..256 {
        let v = i as f32 / 255.0;
        lut_r[i] = (v - IMAGENET_MEAN[0]) / IMAGENET_STD[0];
        lut_g[i] = (v - IMAGENET_MEAN[1]) / IMAGENET_STD[1];
        lut_b[i] = (v - IMAGENET_MEAN[2]) / IMAGENET_STD[2];
    }

    let (plane_r, rest) = out_nchw[..count * 3].split_at_mut(count);
    let (plane_g, plane_b) = rest.split_at_mut(count);

    if content_width == tensor_width && content_height == tensor_height && left == 0 && top == 0 {
        for y in 0..tensor_height {
            let src_row = &source_rgba[y * src_stride_px..y * src_stride_px + tensor_width];
            let dst_off = y * tensor_width;
            for x in 0..tensor_width {
                // RGBA8888 little-endian: byte0=R, byte1=G, byte2=B
                let px = src_row[x];
                plane_r[dst_off + x] = lut_r[(px & 0xff) as usize];
                plane_g[dst_off + x] = lut_g[((px >> 8) & 0xff) as usize];
                plane_b[dst_off + x] = lut_b[((px >> 16) & 0xff) as usize];
            }
        }
    } else {
        let max_cx = (content_width - 1) as isize;
        let max_cy = (content_height - 1) as isize;
        let left_i = left as isize;
        let top_i = top as isize;
        for y in 0..tensor_height {
            let cy = (y as isize - top_i).clamp(0, max_cy) as usize;
            let src_row = &source_rgba[cy * src_stride_px..cy * src_stride_px + content_width];
            let dst_off = y * tensor_width;
            for x in 0..tensor_width {
                let cx = (x as isize - left_i).clamp(0, max_cx) as usize;
                let px = src_row[cx];
                plane_r[dst_off + x] = lut_r[(px & 0xff) as usize];
                plane_g[dst_off + x] = lut_g[((px >> 8) & 0xff) as usize];
                plane_b[dst_off + x] = lut_b[((px >> 16) & 0xff) as usize];
            }
        }
    }
    Ok(())
}

/// Pack ARGB u32 pixels into NHWC [0, 1] RGB floats (`TensorTransfer.packRgb`).
pub fn pack_tflite_nhwc_from_argb(pixels: &[u32], out_nhwc: &mut [f32]) -> Result<(), ProjectionError> {
    let expected = pixels.len().checked_mul(3).ok_or(ProjectionError::InvalidDimensions)?;
    if out_nhwc.len() != expected {
        return Err(ProjectionError::InvalidDimensions);
    }
    let mut lut = [0.0f32; 256];
    for i in 0..256 {
        lut[i] = i as f32 / 255.0;
    }
    for (i, &pixel) in pixels.iter().enumerate() {
        let base = i * 3;
        out_nhwc[base] = lut[((pixel >> 16) & 0xff) as usize];
        out_nhwc[base + 1] = lut[((pixel >> 8) & 0xff) as usize];
        out_nhwc[base + 2] = lut[(pixel & 0xff) as usize];
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pack_qnn_nchw_and_tflite_nhwc_match_expected_channel_order() {
        // RGBA8888 little-endian word for R=255, G=0, B=128, A=255
        let rgba = [0xff80_00ffu32, 0xff00_ff00u32];
        let mut out = [0.0f32; 6];
        pack_qnn_nchw_from_rgba(&rgba, 2, 2, 1, 2, 1, 0, 0, &mut out).unwrap();
        assert!((out[0] - (1.0 - 0.485) / 0.229).abs() < 1e-6);
        assert!((out[2] - (0.0 - 0.456) / 0.224).abs() < 1e-6);
        assert!((out[4] - (128.0 / 255.0 - 0.406) / 0.225).abs() < 1e-6);
        assert!((out[3] - (1.0 - 0.456) / 0.224).abs() < 1e-6);

        let argb = [0xffff_8000u32];
        let mut nhwc = [0.0f32; 3];
        pack_tflite_nhwc_from_argb(&argb, &mut nhwc).unwrap();
        assert_eq!(nhwc, [1.0, 128.0 / 255.0, 0.0]);
    }
}
