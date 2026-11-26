const PATCH: i32 = 14;

fn full(width: i32, height: i32) -> [i32; 7] {
    [width, height, width, height, 0, 0, 0]
}

/// Tensor size, content rectangle and source rotation for the five QNN layouts.
pub fn input_geometry(size: i32, source_width: i32, source_height: i32, aspect: i32) -> Option<[i32; 7]> {
    let budget_edge = size / PATCH;
    if size % PATCH != 0 || !(37..=74).contains(&budget_edge) || source_width <= 0 || source_height <= 0 {
        return None;
    }
    let landscape = source_width >= source_height;
    let (short, long) = (source_width.min(source_height), source_width.max(source_height));
    let wide_unit = budget_edge / 12 * PATCH;
    match aspect {
        0 => return Some(full(size, size)),
        3 if landscape => return Some(full(16 * wide_unit, 9 * wide_unit)),
        3 => return Some(full(9 * wide_unit, 16 * wide_unit)),
        4 => {
            let mut geometry = if f64::from(short) / f64::from(long) <= (27.0_f64 / 64.0).sqrt() {
                full(9 * wide_unit, 16 * wide_unit)
            } else {
                let unit = (f64::from(budget_edge) / 12.0_f64.sqrt()) as i32 * PATCH;
                full(3 * unit, 4 * unit)
            };
            geometry[6] = if source_width > source_height { 90 } else { 0 };
            return Some(geometry);
        }
        1 | 2 => {}
        _ => return None,
    }

    let ratio = f64::from(long) / f64::from(short);
    let budget = budget_edge * budget_edge;
    let desired_long = (f64::from(budget_edge) * ratio.sqrt()).min(2.0 * f64::from(budget_edge));
    let desired_short = (desired_long / ratio).max(1.0);
    let mut long_patches = desired_long.floor() as i32;
    let mut short_patches = desired_short.floor() as i32;
    let mut smallest_error = f64::INFINITY;
    for long in desired_long.floor() as i32..=desired_long.ceil() as i32 {
        for short in desired_short.floor() as i32..=desired_short.ceil() as i32 {
            let area = long * short;
            if area > budget {
                continue;
            }
            let long_error = (f64::from(long) - desired_long) / desired_long;
            let short_error = (f64::from(short) - desired_short) / desired_short;
            let error = long_error * long_error + short_error * short_error;
            if error < smallest_error || (error == smallest_error && area > long_patches * short_patches) {
                long_patches = long;
                short_patches = short;
                smallest_error = error;
            }
        }
    }
    let (width, height) = if landscape { (long_patches, short_patches) } else { (short_patches, long_patches) };
    let (width, height) = (width * PATCH, height * PATCH);
    if aspect == 2 {
        return Some(full(width, height));
    }
    let scale = (f64::from(width) / f64::from(source_width)).min(f64::from(height) / f64::from(source_height));
    let content_width = ((f64::from(source_width) * scale).round() as i32).clamp(1, width);
    let content_height = ((f64::from(source_height) * scale).round() as i32).clamp(1, height);
    Some([
        width,
        height,
        content_width,
        content_height,
        (width - content_width) / 2,
        (height - content_height) / 2,
        0,
    ])
}

#[cfg(test)]
mod tests {
    use super::input_geometry;

    #[test]
    fn qnn_layouts_keep_budget_patch_alignment_and_rotation() {
        for blocks in 37..=74 {
            let size = blocks * 14;
            for aspect in 0..=4 {
                for (width, height) in [(4000, 3000), (720, 1280), (4, 9000), (1036, 1036)] {
                    let [tw, th, cw, ch, x, y, rotation] = input_geometry(size, width, height, aspect).unwrap();
                    assert!(tw * th <= size * size && tw % 14 == 0 && th % 14 == 0);
                    assert!(cw + x <= tw && ch + y <= th);
                    assert_eq!(rotation, if aspect == 4 && width > height { 90 } else { 0 });
                }
            }
        }
        assert_eq!(input_geometry(518, 1600, 900, 4), Some([378, 672, 378, 672, 0, 0, 90]));
        assert_eq!(input_geometry(519, 1600, 900, 0), None);
    }
}
