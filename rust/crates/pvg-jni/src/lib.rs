//! Single-source-of-truth JNI bindings for `com.local.parallelvrgallerypro.RustCore`.
//!
//! Built for both Android 4 ABIs (`libpvg_core.so`) and Host JVM unit tests (`pvg_core.dll` / `libpvg_core.so`).

#[cfg(target_os = "android")]
mod android_bitmap;

use std::path::{Path, PathBuf};
use std::ptr;
use std::slice;

use jni::JNIEnv;
use jni::objects::{
    JByteArray, JByteBuffer, JClass, JFloatArray, JIntArray, JLongArray, JObjectArray, JString,
    ReleaseMode,
};
use jni::sys::{
    JNI_FALSE, JNI_TRUE, jboolean, jbyte, jfloat, jfloatArray, jint, jintArray, jlong, jlongArray,
    jstring,
};
use pvg_core::cache::{self, ImagePublication};
use pvg_core::depth::{self, Interpolation, ModelDepthError};
use pvg_core::legacy_stereo::{PhoneDepthFilter, filter_phone_depth, project_phone_stereo};
use pvg_core::pixel::pack_tflite_nhwc_from_argb;
use pvg_core::projection::{ProjectionError, project_legacy, project_subpixel};
use pvg_core::qnn_geometry::input_geometry;
use pvg_core::reference::project_reference;
use pvg_core::video_timing::{
    SegmentPlan, SegmentRange, VideoTimeline, VideoTimingError, check_segment_coverage,
    validate_segment_timestamp,
};

const ILLEGAL_ARG: &str = "java/lang/IllegalArgumentException";
const ILLEGAL_STATE: &str = "java/lang/IllegalStateException";
const IO_EXCEPTION: &str = "java/io/IOException";
const OOM_ERROR: &str = "java/lang/OutOfMemoryError";

fn throw(env: &mut JNIEnv<'_>, class: &str, message: &str) {
    let _ = env.throw_new(class, message);
}

fn throw_projection_error(env: &mut JNIEnv<'_>, error: ProjectionError) {
    match error {
        ProjectionError::InvalidDimensions => throw(env, ILLEGAL_ARG, "Invalid image dimensions"),
        ProjectionError::InvalidDepthLength => throw(env, ILLEGAL_ARG, "Invalid depth buffer length"),
        ProjectionError::InvalidScale => throw(env, ILLEGAL_ARG, "Invalid depth scale"),
        ProjectionError::AllocationFailed => throw(env, OOM_ERROR, "Native allocation failed"),
    }
}

pub(crate) fn read_float_array(env: &JNIEnv<'_>, array: &JFloatArray<'_>) -> Option<Vec<f32>> {
    let len = usize::try_from(env.get_array_length(array).ok()?).ok()?;
    let mut values = Vec::new();
    values.try_reserve_exact(len).ok()?;
    values.resize(len, 0.0);
    env.get_float_array_region(array, 0, &mut values).ok()?;
    Some(values)
}

fn read_int_array(env: &JNIEnv<'_>, array: &JIntArray<'_>) -> Option<Vec<u32>> {
    let len = usize::try_from(env.get_array_length(array).ok()?).ok()?;
    let mut values = Vec::new();
    values.try_reserve_exact(len).ok()?;
    values.resize(len, 0i32);
    env.get_int_array_region(array, 0, &mut values).ok()?;
    let ptr = values.as_mut_ptr().cast::<u32>();
    let cap = values.capacity();
    std::mem::forget(values);
    Some(unsafe { Vec::from_raw_parts(ptr, len, cap) })
}

fn new_int_array(env: &JNIEnv<'_>, values: &[u32]) -> jintArray {
    let Ok(len) = jint::try_from(values.len()) else {
        return ptr::null_mut();
    };
    let Ok(out) = env.new_int_array(len) else {
        return ptr::null_mut();
    };
    let signed = unsafe { slice::from_raw_parts(values.as_ptr().cast::<jint>(), values.len()) };
    if env.set_int_array_region(&out, 0, signed).is_err() {
        return ptr::null_mut();
    }
    out.into_raw()
}

fn new_float_array(env: &JNIEnv<'_>, values: &[f32]) -> jfloatArray {
    let Ok(len) = jint::try_from(values.len()) else {
        return ptr::null_mut();
    };
    let Ok(out) = env.new_float_array(len) else {
        return ptr::null_mut();
    };
    if env.set_float_array_region(&out, 0, values).is_err() {
        return ptr::null_mut();
    }
    out.into_raw()
}

fn read_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    Some(env.get_string(value).ok()?.to_string_lossy().into_owned())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeAbiInfo(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let info = format!(
        "pvg_core arch={} os={} zero_copy_bitmap={}",
        std::env::consts::ARCH,
        std::env::consts::OS,
        cfg!(target_os = "android")
    );
    env.new_string(info).map_or(ptr::null_mut(), JString::into_raw)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectComfy(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    source: JIntArray<'_>,
    depth: JFloatArray<'_>,
    width: jint,
    height: jint,
    scale: jfloat,
    subpixel: jboolean,
) -> jintArray {
    let (Ok(w), Ok(h)) = (usize::try_from(width), usize::try_from(height)) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let (Some(src), Some(d)) = (read_int_array(&env, &source), read_float_array(&env, &depth)) else {
        throw(&mut env, OOM_ERROR, "Failed to read projection inputs");
        return ptr::null_mut();
    };
    let result = if subpixel != JNI_FALSE {
        project_subpixel(&src, w, h, &d, scale)
    } else {
        project_legacy(&src, w, h, &d, scale)
    };
    drop(src);
    drop(d);
    match result {
        Ok(pixels) => new_int_array(&env, &pixels),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectReference(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    source: JIntArray<'_>,
    depth: JFloatArray<'_>,
    width: jint,
    height: jint,
    depth_width: jint,
    depth_height: jint,
    scale: jfloat,
    pixels_out: JIntArray<'_>,
    holes_out: JByteArray<'_>,
) {
    let (Ok(w), Ok(h), Ok(dw), Ok(dh)) = (
        usize::try_from(width),
        usize::try_from(height),
        usize::try_from(depth_width),
        usize::try_from(depth_height),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return;
    };
    let (Some(src), Some(d)) = (read_int_array(&env, &source), read_float_array(&env, &depth)) else {
        throw(&mut env, OOM_ERROR, "Failed to read reference inputs");
        return;
    };
    let raster = match project_reference(&src, w, h, &d, dw, dh, scale) {
        Ok(r) => r,
        Err(err) => {
            throw_projection_error(&mut env, err);
            return;
        }
    };
    drop(src);
    drop(d);
    let signed_px =
        unsafe { slice::from_raw_parts(raster.pixels.as_ptr().cast::<jint>(), raster.pixels.len()) };
    let signed_holes =
        unsafe { slice::from_raw_parts(raster.holes.as_ptr().cast::<jbyte>(), raster.holes.len()) };
    if env.set_int_array_region(&pixels_out, 0, signed_px).is_err()
        || env.set_byte_array_region(&holes_out, 0, signed_holes).is_err()
    {
        throw(&mut env, ILLEGAL_ARG, "Output buffer size mismatch");
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectPhoneStereo(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    source: JIntArray<'_>,
    depth: JFloatArray<'_>,
    width: jint,
    height: jint,
    depth_width: jint,
    depth_height: jint,
    scale: jfloat,
    fill_radius: jint,
) -> jintArray {
    let (Ok(w), Ok(h), Ok(dw), Ok(dh)) = (
        usize::try_from(width),
        usize::try_from(height),
        usize::try_from(depth_width),
        usize::try_from(depth_height),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let (Some(src), Some(d)) = (read_int_array(&env, &source), read_float_array(&env, &depth)) else {
        throw(&mut env, OOM_ERROR, "Failed to read phone stereo inputs");
        return ptr::null_mut();
    };
    let result = project_phone_stereo(&src, w, h, &d, dw, dh, scale, fill_radius);
    drop(src);
    drop(d);
    match result {
        Ok(pixels) => new_int_array(&env, &pixels),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeFilterPhoneDepth(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    depth: JFloatArray<'_>,
    width: jint,
    height: jint,
    diameter: jint,
    invert: jboolean,
    centered: jboolean,
) -> jfloatArray {
    let (Ok(w), Ok(h)) = (usize::try_from(width), usize::try_from(height)) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let Some(d) = read_float_array(&env, &depth) else {
        throw(&mut env, OOM_ERROR, "Failed to read depth array");
        return ptr::null_mut();
    };
    let mode = if centered != JNI_FALSE {
        PhoneDepthFilter::CenteredV7
    } else {
        PhoneDepthFilter::LegacyV6
    };
    match filter_phone_depth(&d, w, h, diameter, invert != JNI_FALSE, mode) {
        Ok(out) => new_float_array(&env, &out),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeNormalizeModelDepth(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JFloatArray<'_>,
    strict: jboolean,
) -> jfloat {
    let result = match unsafe { env.get_array_elements(&input, ReleaseMode::CopyBack) } {
        Ok(mut values) => depth::normalize_model_depth(&mut values, strict != JNI_FALSE),
        Err(_) => {
            throw(&mut env, ILLEGAL_STATE, "Could not lock float array");
            return f32::NAN;
        }
    };
    match result {
        Ok(range) => range,
        Err(ModelDepthError::NonFinite) => {
            throw(&mut env, ILLEGAL_STATE, "QNN GPU 深度含无效数值 / Non-finite QNN GPU depth");
            f32::NAN
        }
        Err(ModelDepthError::InsufficientRange) => {
            throw(&mut env, ILLEGAL_STATE, "QNN GPU 深度变化不足 / Flat QNN GPU depth");
            f32::NAN
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeReadNormalizedDirect(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    direct_buffer: JByteBuffer<'_>,
    count: jint,
    out_values: JFloatArray<'_>,
) -> jfloat {
    let Some(n) = usize::try_from(count).ok().filter(|&n| n > 0) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid count");
        return f32::NAN;
    };
    let (Ok(ptr), Ok(byte_cap)) = (
        env.get_direct_buffer_address(&direct_buffer),
        env.get_direct_buffer_capacity(&direct_buffer),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Buffer is not direct");
        return f32::NAN;
    };
    if ptr.is_null() || byte_cap != n * 4 || (ptr as usize) % align_of::<f32>() != 0 {
        throw(&mut env, ILLEGAL_ARG, "Invalid direct buffer capacity or alignment");
        return f32::NAN;
    }
    let src = unsafe { slice::from_raw_parts(ptr.cast::<f32>(), n) };
    let mut values = match depth::allocate::<f32>(n) {
        Ok(v) => v,
        Err(_) => {
            throw(&mut env, OOM_ERROR, "Allocation failed");
            return f32::NAN;
        }
    };
    values.extend_from_slice(src);
    let range = match depth::normalize_model_depth(&mut values, false) {
        Ok(r) => r,
        Err(_) => f32::NAN,
    };
    if env.set_float_array_region(&out_values, 0, &values).is_err() {
        throw(&mut env, ILLEGAL_ARG, "Output array mismatch");
        return f32::NAN;
    }
    range
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativePackTfliteRgbDirect(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    pixels: JIntArray<'_>,
    out_direct_buffer: JByteBuffer<'_>,
) -> jboolean {
    let Some(src) = read_int_array(&env, &pixels) else {
        return JNI_FALSE;
    };
    let (Ok(ptr), Ok(byte_cap)) = (
        env.get_direct_buffer_address(&out_direct_buffer),
        env.get_direct_buffer_capacity(&out_direct_buffer),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Target buffer is not direct");
        return JNI_FALSE;
    };
    if ptr.is_null() || (ptr as usize) % align_of::<f32>() != 0 || byte_cap % 4 != 0 {
        return JNI_FALSE;
    }
    let out = unsafe { slice::from_raw_parts_mut(ptr.cast::<f32>(), byte_cap / 4) };
    if pack_tflite_nhwc_from_argb(&src, out).is_ok() {
        JNI_TRUE
    } else {
        throw(&mut env, ILLEGAL_ARG, "Target float buffer capacity mismatch");
        JNI_FALSE
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeRotateDepthCcw90(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    values: JFloatArray<'_>,
    width: jint,
    height: jint,
) -> jfloatArray {
    let (Ok(w), Ok(h)) = (usize::try_from(width), usize::try_from(height)) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let Some(src) = read_float_array(&env, &values) else {
        throw(&mut env, OOM_ERROR, "Failed to read depth array");
        return ptr::null_mut();
    };
    match depth::rotate_ccw_90(&src, w, h) {
        Ok(turned) => new_float_array(&env, &turned),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeExtractQnnDepthDirect(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    direct_buffer: JByteBuffer<'_>,
    tensor_width: jint,
    tensor_height: jint,
    content_width: jint,
    content_height: jint,
    left: jint,
    top: jint,
    rotate_ccw: jboolean,
) -> jfloatArray {
    let (Ok(tw), Ok(th), Ok(cw), Ok(ch), Ok(left), Ok(top)) = (
        usize::try_from(tensor_width),
        usize::try_from(tensor_height),
        usize::try_from(content_width),
        usize::try_from(content_height),
        usize::try_from(left),
        usize::try_from(top),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid QNN depth geometry");
        return ptr::null_mut();
    };
    let (Ok(ptr), Ok(byte_cap)) = (
        env.get_direct_buffer_address(&direct_buffer),
        env.get_direct_buffer_capacity(&direct_buffer),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "QNN depth buffer is not direct");
        return ptr::null_mut();
    };
    if ptr.is_null() || (ptr as usize) % align_of::<f32>() != 0 || byte_cap % 4 != 0 {
        throw(&mut env, ILLEGAL_ARG, "Invalid direct buffer alignment");
        return ptr::null_mut();
    }
    let tensor = unsafe { slice::from_raw_parts(ptr.cast::<f32>(), byte_cap / 4) };
    match depth::extract_and_normalize_qnn(tensor, tw, th, cw, ch, left, top, rotate_ccw != JNI_FALSE) {
        Ok(values) => new_float_array(&env, &values),
        Err(Ok(ModelDepthError::NonFinite)) => {
            throw(&mut env, ILLEGAL_STATE, "QNN GPU 深度含无效数值 / Non-finite QNN GPU depth");
            ptr::null_mut()
        }
        Err(Ok(ModelDepthError::InsufficientRange)) => {
            throw(&mut env, ILLEGAL_STATE, "QNN GPU 深度变化不足 / Flat QNN GPU depth");
            ptr::null_mut()
        }
        Err(Err(err)) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeDisparitySpan(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JFloatArray<'_>,
) -> jfloat {
    match unsafe { env.get_array_elements(&input, ReleaseMode::NoCopyBack) } {
        Ok(values) => depth::disparity_span(&values).unwrap_or(0.0),
        Err(_) => 0.0,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeResizeDepth(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JFloatArray<'_>,
    source_width: jint,
    source_height: jint,
    width: jint,
    height: jint,
    method: jint,
) -> jfloatArray {
    let (Ok(sw), Ok(sh), Ok(dw), Ok(dh)) = (
        usize::try_from(source_width),
        usize::try_from(source_height),
        usize::try_from(width),
        usize::try_from(height),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let Some(interp) = Interpolation::from_code(method) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid interpolation method");
        return ptr::null_mut();
    };
    let Some(values) = read_float_array(&env, &input) else {
        throw(&mut env, OOM_ERROR, "Failed to read depth array");
        return ptr::null_mut();
    };
    match depth::resize(&values, sw, sh, dw, dh, interp) {
        Ok(out) => new_float_array(&env, &out),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeDepthTargetSize(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    width: jint,
    height: jint,
    long_edge: jint,
    max_pixels: jlong,
) -> jlong {
    match depth::target_size(width, height, long_edge, max_pixels) {
        Some((w, h)) => (jlong::from(w) << 32) | (jlong::from(h) & 0xffff_ffff),
        None => {
            throw(&mut env, ILLEGAL_ARG, "Invalid target size parameters");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativePostProcessDepth(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JFloatArray<'_>,
    source_width: jint,
    source_height: jint,
    width: jint,
    height: jint,
    mode: jint,
    diameter: jint,
    method: jint,
    invert: jboolean,
    resize_first: jboolean,
) -> jfloatArray {
    let (Ok(sw), Ok(sh), Ok(dw), Ok(dh)) = (
        usize::try_from(source_width),
        usize::try_from(source_height),
        usize::try_from(width),
        usize::try_from(height),
    ) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid dimensions");
        return ptr::null_mut();
    };
    let Some(interp) = Interpolation::from_code(method) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid interpolation method");
        return ptr::null_mut();
    };
    if !(0..=5).contains(&mode) {
        throw(&mut env, ILLEGAL_ARG, "Invalid edge mode");
        return ptr::null_mut();
    }
    let Some(source) = read_float_array(&env, &input) else {
        throw(&mut env, OOM_ERROR, "Failed to read depth array");
        return ptr::null_mut();
    };
    let process = depth::PostProcess {
        target: Some((dw, dh)),
        method: interp,
        resize_first: resize_first != JNI_FALSE,
        mode,
        invert: invert != JNI_FALSE,
        diameter,
    };
    match process.apply(source, sw, sh) {
        Ok(out) => new_float_array(&env, &out),
        Err(err) => {
            throw_projection_error(&mut env, err);
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeSmoothTemporal(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    current: JFloatArray<'_>,
    previous: JFloatArray<'_>,
    previous_weight: jfloat,
    scene_cut: jfloat,
    motion: jfloat,
) -> jboolean {
    let (Some(mut values), Some(prev)) = (read_float_array(&env, &current), read_float_array(&env, &previous)) else {
        return JNI_FALSE;
    };
    if !depth::smooth_temporal(&mut values, &prev, previous_weight, scene_cut, motion) {
        return JNI_FALSE;
    }
    if env.set_float_array_region(&current, 0, &values).is_err() {
        return JNI_FALSE;
    }
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeMapDepthDirection(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    input: JFloatArray<'_>,
    invert: jboolean,
    clamp: jboolean,
) -> jfloatArray {
    let Some(mut values) = read_float_array(&env, &input) else {
        return ptr::null_mut();
    };
    depth::map_direction(&mut values, invert != JNI_FALSE, clamp != JNI_FALSE);
    new_float_array(&env, &values)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeQnnInputGeometry(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    size: jint,
    source_width: jint,
    source_height: jint,
    aspect: jint,
) -> jintArray {
    let Some(geometry) = input_geometry(size, source_width, source_height, aspect) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid QNN input geometry parameters");
        return ptr::null_mut();
    };
    let Ok(out) = env.new_int_array(geometry.len() as jint) else {
        return ptr::null_mut();
    };
    if env.set_int_array_region(&out, 0, &geometry).is_err() {
        return ptr::null_mut();
    }
    out.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeSortVideoTimeline(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    decode_order: JLongArray<'_>,
) -> jlongArray {
    let Ok(len) = env.get_array_length(&decode_order) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid timestamp array");
        return ptr::null_mut();
    };
    let mut timestamps = Vec::new();
    if timestamps.try_reserve_exact(len as usize).is_err() {
        throw(&mut env, OOM_ERROR, "Allocation failed");
        return ptr::null_mut();
    }
    timestamps.resize(len as usize, 0);
    if env.get_long_array_region(&decode_order, 0, &mut timestamps).is_err() {
        throw(&mut env, ILLEGAL_ARG, "Failed to read timestamps");
        return ptr::null_mut();
    }
    match VideoTimeline::new(timestamps) {
        Ok(timeline) => {
            let Ok(out) = env.new_long_array(len) else {
                return ptr::null_mut();
            };
            if env.set_long_array_region(&out, 0, timeline.timestamps_us()).is_err() {
                return ptr::null_mut();
            }
            out.into_raw()
        }
        Err(err) => {
            throw(&mut env, ILLEGAL_ARG, &err.to_string());
            ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeCheckSegmentCoverage(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    packed: JIntArray<'_>,
    total_frames: jint,
) {
    let Ok(len) = env.get_array_length(&packed) else {
        throw(&mut env, ILLEGAL_ARG, "Invalid segment coverage array");
        return;
    };
    if len % 2 != 0 || total_frames < 0 {
        throw(&mut env, ILLEGAL_ARG, "Invalid segment coverage parameters");
        return;
    }
    let mut raw = vec![0i32; len as usize];
    if env.get_int_array_region(&packed, 0, &mut raw).is_err() {
        throw(&mut env, ILLEGAL_ARG, "Failed to read segment ranges");
        return;
    }
    let mut segments = Vec::with_capacity(raw.len() / 2);
    for pair in raw.chunks_exact(2) {
        let (Ok(first_frame), Ok(frame_count)) = (usize::try_from(pair[0]), usize::try_from(pair[1])) else {
            throw(&mut env, ILLEGAL_ARG, "Invalid negative segment range");
            return;
        };
        segments.push(SegmentRange { first_frame, frame_count });
    }
    if let Err(err) = check_segment_coverage(&segments, total_frames as usize) {
        throw(&mut env, ILLEGAL_ARG, &err.to_string());
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeValidateSegmentTimestamp(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    local: jlong,
    previous: jlong,
    first: jlong,
    expected: jlong,
    initial: jboolean,
    sync: jboolean,
) -> jint {
    match validate_segment_timestamp(local, previous, first, expected, 0, initial != JNI_FALSE, sync != JNI_FALSE) {
        Ok(()) => 0,
        Err(VideoTimingError::NonmonotonicLocalPts { .. }) => 1,
        Err(VideoTimingError::MissingInitialSync) => 2,
        Err(_) => 3,
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeSegmentRange(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    total: jint,
    segment_size: jint,
    index: jint,
) -> jlong {
    let (Ok(total), Ok(segment_size), Ok(index)) =
        (usize::try_from(total), usize::try_from(segment_size), usize::try_from(index))
    else {
        return -1;
    };
    let Ok(plan) = SegmentPlan::new(total, segment_size) else {
        return -1;
    };
    let Some(range) = plan.segment(index) else {
        return -1;
    };
    let end = range.first_frame + range.frame_count;
    ((range.first_frame as jlong) << 32) | (end as jlong & 0xffff_ffff)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeWriteDepthCheckpoint(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    path: JString<'_>,
    width: jint,
    height: jint,
    values: JFloatArray<'_>,
) {
    let (Some(path), Ok(w), Ok(h)) = (read_jstring(&mut env, &path), usize::try_from(width), usize::try_from(height))
    else {
        throw(&mut env, IO_EXCEPTION, "Invalid video depth checkpoint arguments");
        return;
    };
    let Some(samples) = read_float_array(&env, &values) else {
        throw(&mut env, IO_EXCEPTION, "Cannot transfer video depth checkpoint");
        return;
    };
    if let Err(err) = cache::write_depth_checkpoint(Path::new(&path), w, h, &samples) {
        throw(&mut env, IO_EXCEPTION, &err.to_string());
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeReadDepthCheckpoint(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    path: JString<'_>,
    shape_out: JIntArray<'_>,
) -> jfloatArray {
    let Some(path) = read_jstring(&mut env, &path) else {
        throw(&mut env, IO_EXCEPTION, "Invalid checkpoint path");
        return ptr::null_mut();
    };
    let (w, h, values) = match cache::read_depth_checkpoint(Path::new(&path)) {
        Ok(decoded) => decoded,
        Err(err) => {
            let exc = if err.kind() == std::io::ErrorKind::InvalidData {
                ILLEGAL_STATE
            } else {
                IO_EXCEPTION
            };
            throw(&mut env, exc, &err.to_string());
            return ptr::null_mut();
        }
    };
    if env.set_int_array_region(&shape_out, 0, &[w as jint, h as jint]).is_err() {
        throw(&mut env, ILLEGAL_ARG, "Invalid shape output array");
        return ptr::null_mut();
    }
    new_float_array(&env, &values)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeReclaimFrameJpegs(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    dir_path: JString<'_>,
    first: jint,
    end: jint,
) {
    if first < 0 || end < first {
        return;
    }
    if let Some(root) = read_jstring(&mut env, &dir_path) {
        cache::reclaim_frame_jpegs(&PathBuf::from(root), first, end);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativePublishImage(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    fields: JObjectArray<'_>,
    recover: jboolean,
) -> jstring {
    let publication = (|| {
        let size = env.get_array_length(&fields).ok()?;
        if !(5..=20_005).contains(&size) || (size - 5) % 2 != 0 {
            return None;
        }
        let mut values = Vec::with_capacity(size as usize);
        for i in 0..size {
            let elem = env.get_object_array_element(&fields, i).ok()?;
            let elem = env.auto_local(JString::from(elem));
            values.push(read_jstring(&mut env, &elem)?);
        }
        let mut iter = values.into_iter();
        let [id, work, target, journal, done] = std::array::from_fn(|_| iter.next().unwrap_or_default());
        let mut backups = Vec::with_capacity((size as usize - 5) / 2);
        while let (Some(old), Some(backup)) = (iter.next(), iter.next()) {
            backups.push((old.into(), backup.into()));
        }
        Some(ImagePublication {
            id,
            work: work.into(),
            target: target.into(),
            journal: journal.into(),
            done: done.into(),
            backups,
        })
    })();
    let result = publication
        .ok_or_else(|| "Invalid image publication journal".to_string())
        .and_then(|pub_tx| {
            if recover == JNI_FALSE {
                pub_tx.commit()
            } else {
                pub_tx.recover().map_err(|err| err.to_string())
            }
        });
    result
        .err()
        .map_or(ptr::null_mut(), |msg| env.new_string(msg).map_or(ptr::null_mut(), JString::into_raw))
}
