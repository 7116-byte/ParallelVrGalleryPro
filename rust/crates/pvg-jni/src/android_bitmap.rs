//! Zero-copy AndroidBitmap JNI entrypoints (`libjnigraphics.so`, Android API 30+).

use std::ffi::c_void;
use std::slice;

use jni::JNIEnv;
use jni::objects::{JByteArray, JByteBuffer, JClass, JFloatArray, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jfloat, jint, jobject};
use pvg_core::legacy_stereo::project_phone_stereo_into;
use pvg_core::pixel::pack_qnn_nchw_from_rgba;
use pvg_core::projection::{project_legacy_into, project_subpixel_into};
use pvg_core::reference::{bgr_to_rgba_strided, project_reference_rgba_to_bgr};

const ANDROID_BITMAP_RESULT_SUCCESS: i32 = 0;
const ANDROID_BITMAP_FORMAT_RGBA_8888: i32 = 1;

#[repr(C)]
#[derive(Default)]
struct AndroidBitmapInfo {
    width: u32,
    height: u32,
    stride: u32,
    format: i32,
    flags: u32,
}

#[link(name = "jnigraphics")]
unsafe extern "C" {
    fn AndroidBitmap_getInfo(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jobject,
        info: *mut AndroidBitmapInfo,
    ) -> i32;
    fn AndroidBitmap_lockPixels(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jobject,
        addr_ptr: *mut *mut c_void,
    ) -> i32;
    fn AndroidBitmap_unlockPixels(env: *mut jni::sys::JNIEnv, jbitmap: jobject) -> i32;
}

struct LockedBitmap {
    env: *mut jni::sys::JNIEnv,
    bitmap: jobject,
    ptr: *mut u32,
    width: usize,
    height: usize,
    stride_px: usize,
}

impl LockedBitmap {
    fn lock(env: &JNIEnv<'_>, bitmap: &JObject<'_>) -> Option<Self> {
        if bitmap.is_null() {
            return None;
        }
        let raw_env = env.get_raw();
        let raw_bmp = bitmap.as_raw();
        let mut info = AndroidBitmapInfo::default();
        if unsafe { AndroidBitmap_getInfo(raw_env, raw_bmp, &mut info) } != ANDROID_BITMAP_RESULT_SUCCESS {
            return None;
        }
        if info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
            || info.width == 0
            || info.height == 0
            || info.stride % 4 != 0
            || info.stride < info.width.checked_mul(4)?
        {
            return None;
        }
        let mut raw_ptr: *mut c_void = std::ptr::null_mut();
        if unsafe { AndroidBitmap_lockPixels(raw_env, raw_bmp, &mut raw_ptr) } != ANDROID_BITMAP_RESULT_SUCCESS
            || raw_ptr.is_null()
            || (raw_ptr as usize) % align_of::<u32>() != 0
        {
            if !raw_ptr.is_null() {
                unsafe { AndroidBitmap_unlockPixels(raw_env, raw_bmp) };
            }
            return None;
        }
        Some(Self {
            env: raw_env,
            bitmap: raw_bmp,
            ptr: raw_ptr.cast::<u32>(),
            width: info.width as usize,
            height: info.height as usize,
            stride_px: (info.stride / 4) as usize,
        })
    }

    fn as_slice(&self) -> &[u32] {
        let len = self.stride_px * self.height;
        unsafe { slice::from_raw_parts(self.ptr, len) }
    }

    fn as_mut_slice(&mut self) -> &mut [u32] {
        let len = self.stride_px * self.height;
        unsafe { slice::from_raw_parts_mut(self.ptr, len) }
    }
}

impl Drop for LockedBitmap {
    fn drop(&mut self) {
        unsafe {
            AndroidBitmap_unlockPixels(self.env, self.bitmap);
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectComfyBitmap(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    source_bitmap: JObject<'_>,
    dest_bitmap: JObject<'_>,
    depth: JFloatArray<'_>,
    scale: jfloat,
    subpixel: jboolean,
) -> jboolean {
    let Some(src) = LockedBitmap::lock(&env, &source_bitmap) else {
        return JNI_FALSE;
    };
    let Some(mut dst) = LockedBitmap::lock(&env, &dest_bitmap) else {
        return JNI_FALSE;
    };
    if src.width != dst.width || src.height != dst.height {
        return JNI_FALSE;
    }
    let Some(depth_values) = crate::read_float_array(&env, &depth) else {
        return JNI_FALSE;
    };
    let (dst_stride, w, h) = (dst.stride_px, src.width, src.height);
    let res = if subpixel != JNI_FALSE {
        project_subpixel_into(
            src.as_slice(),
            src.stride_px,
            dst.as_mut_slice(),
            dst_stride,
            w,
            h,
            &depth_values,
            scale,
        )
    } else {
        project_legacy_into(
            src.as_slice(),
            src.stride_px,
            dst.as_mut_slice(),
            dst_stride,
            w,
            h,
            &depth_values,
            scale,
        )
    };
    if res.is_ok() { JNI_TRUE } else { JNI_FALSE }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectPhoneBitmap(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    source_bitmap: JObject<'_>,
    dest_bitmap: JObject<'_>,
    depth: JFloatArray<'_>,
    depth_width: jint,
    depth_height: jint,
    scale: jfloat,
    fill_radius: jint,
) -> jboolean {
    let (Ok(dw), Ok(dh)) = (usize::try_from(depth_width), usize::try_from(depth_height)) else {
        return JNI_FALSE;
    };
    let Some(src) = LockedBitmap::lock(&env, &source_bitmap) else {
        return JNI_FALSE;
    };
    let Some(mut dst) = LockedBitmap::lock(&env, &dest_bitmap) else {
        return JNI_FALSE;
    };
    if src.width != dst.width || src.height != dst.height {
        return JNI_FALSE;
    }
    let Some(depth_values) = crate::read_float_array(&env, &depth) else {
        return JNI_FALSE;
    };
    let (dst_stride, w, h) = (dst.stride_px, src.width, src.height);
    let res = project_phone_stereo_into(
        src.as_slice(),
        src.stride_px,
        dst.as_mut_slice(),
        dst_stride,
        w,
        h,
        &depth_values,
        dw,
        dh,
        scale,
        fill_radius,
    );
    if res.is_ok() { JNI_TRUE } else { JNI_FALSE }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeProjectReferenceBitmapToBgr(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    source_bitmap: JObject<'_>,
    depth: JFloatArray<'_>,
    depth_width: jint,
    depth_height: jint,
    scale: jfloat,
    bgr_out: JByteArray<'_>,
    holes_out: JByteArray<'_>,
) -> jboolean {
    let (Ok(dw), Ok(dh)) = (usize::try_from(depth_width), usize::try_from(depth_height)) else {
        return JNI_FALSE;
    };
    let Some(src) = LockedBitmap::lock(&env, &source_bitmap) else {
        return JNI_FALSE;
    };
    let Some(pixel_count) = src.width.checked_mul(src.height) else {
        return JNI_FALSE;
    };
    let Some(bgr_len) = pixel_count.checked_mul(3) else {
        return JNI_FALSE;
    };
    if env.get_array_length(&bgr_out).ok() != jint::try_from(bgr_len).ok()
        || env.get_array_length(&holes_out).ok() != jint::try_from(pixel_count).ok()
    {
        return JNI_FALSE;
    }
    let Some(depth_values) = crate::read_float_array(&env, &depth) else {
        return JNI_FALSE;
    };
    let mut bgr = Vec::new();
    let mut holes = Vec::new();
    if bgr.try_reserve_exact(bgr_len).is_err() || holes.try_reserve_exact(pixel_count).is_err() {
        return JNI_FALSE;
    }
    bgr.resize(bgr_len, 0u8);
    holes.resize(pixel_count, 0u8);
    if project_reference_rgba_to_bgr(
        src.as_slice(),
        src.stride_px,
        src.width,
        src.height,
        &depth_values,
        dw,
        dh,
        scale,
        &mut bgr,
        &mut holes,
    )
    .is_err()
    {
        return JNI_FALSE;
    }
    let bgr_i8 = unsafe { slice::from_raw_parts(bgr.as_ptr().cast::<jni::sys::jbyte>(), bgr.len()) };
    let holes_i8 = unsafe { slice::from_raw_parts(holes.as_ptr().cast::<jni::sys::jbyte>(), holes.len()) };
    if env.set_byte_array_region(&bgr_out, 0, bgr_i8).is_err()
        || env.set_byte_array_region(&holes_out, 0, holes_i8).is_err()
    {
        return JNI_FALSE;
    }
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativeWriteBgrToBitmap(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    bgr_in: JByteArray<'_>,
    dest_bitmap: JObject<'_>,
) -> jboolean {
    let Some(mut dst) = LockedBitmap::lock(&env, &dest_bitmap) else {
        return JNI_FALSE;
    };
    let Some(bgr_len) = dst.width.checked_mul(dst.height).and_then(|n| n.checked_mul(3)) else {
        return JNI_FALSE;
    };
    if env.get_array_length(&bgr_in).ok() != jint::try_from(bgr_len).ok() {
        return JNI_FALSE;
    }
    let mut bgr = Vec::new();
    if bgr.try_reserve_exact(bgr_len).is_err() {
        return JNI_FALSE;
    }
    bgr.resize(bgr_len, 0u8);
    let bgr_i8 = unsafe { slice::from_raw_parts_mut(bgr.as_mut_ptr().cast::<jni::sys::jbyte>(), bgr_len) };
    if env.get_byte_array_region(&bgr_in, 0, bgr_i8).is_err() {
        return JNI_FALSE;
    }
    let (dst_stride, w, h) = (dst.stride_px, dst.width, dst.height);
    if bgr_to_rgba_strided(&bgr, dst.as_mut_slice(), dst_stride, w, h).is_ok() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_local_parallelvrgallerypro_RustCore_nativePackQnnRgbBitmap(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    source_bitmap: JObject<'_>,
    out_direct_buffer: JByteBuffer<'_>,
    tensor_width: jint,
    tensor_height: jint,
    left: jint,
    top: jint,
) -> jboolean {
    let (Ok(tw), Ok(th), Ok(left), Ok(top)) = (
        usize::try_from(tensor_width),
        usize::try_from(tensor_height),
        usize::try_from(left),
        usize::try_from(top),
    ) else {
        return JNI_FALSE;
    };
    let Some(src) = LockedBitmap::lock(&env, &source_bitmap) else {
        return JNI_FALSE;
    };
    let Ok(ptr) = env.get_direct_buffer_address(&out_direct_buffer) else {
        return JNI_FALSE;
    };
    let Ok(byte_cap) = env.get_direct_buffer_capacity(&out_direct_buffer) else {
        return JNI_FALSE;
    };
    if ptr.is_null() || (ptr as usize) % align_of::<f32>() != 0 || byte_cap % 4 != 0 {
        return JNI_FALSE;
    }
    let out_floats = unsafe { slice::from_raw_parts_mut(ptr.cast::<f32>(), byte_cap / 4) };
    if pack_qnn_nchw_from_rgba(
        src.as_slice(),
        src.stride_px,
        src.width,
        src.height,
        tw,
        th,
        left,
        top,
        out_floats,
    )
    .is_ok()
    {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
