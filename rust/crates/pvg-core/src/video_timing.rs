//! Presentation order, segment boundaries and timestamp validation for SBS remuxing.

use std::fmt;
use std::ops::Range;

/// Segment timestamp rounding can include two conversions to the 90 kHz MP4 clock.
pub const MAX_SEGMENT_PTS_DRIFT_US: u64 = 23;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VideoTimingError {
    EmptyTimeline,
    NegativePts(i64),
    DuplicatePts(i64),
    EmptyPlan,
    InvalidSegmentRange(usize),
    IncompleteCoverage { expected: usize, covered: usize },
    TooManyVideoSamples(usize),
    NonmonotonicLocalPts { previous: i64, received: i64 },
    MissingInitialSync,
    TimestampDrift { frame: usize, expected: i64, actual: i64 },
    IncompleteSegment { expected: usize, received: usize },
}

impl fmt::Display for VideoTimingError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match *self {
            Self::EmptyTimeline => write!(f, "Video has no samples"),
            Self::NegativePts(_) => write!(f, "Negative presentation timestamp"),
            Self::DuplicatePts(_) => write!(f, "Duplicate video timestamps cannot be mapped safely to cached frames"),
            Self::EmptyPlan => write!(f, "Video and segment must each have at least one frame"),
            Self::InvalidSegmentRange(first_frame) => write!(f, "Invalid video segment range at frame {first_frame}"),
            Self::IncompleteCoverage { expected, covered } => write!(f, "Video segments cover {covered} of {expected} frames"),
            Self::TooManyVideoSamples(expected) => write!(f, "Too many frames (expected {expected})"),
            Self::NonmonotonicLocalPts { .. } => write!(f, "Nonmonotonic AVC timestamps"),
            Self::MissingInitialSync => write!(f, "Video segment does not start with a sync sample"),
            Self::TimestampDrift { frame, .. } => write!(f, "Unexpected AVC sample timestamp at frame {frame}"),
            Self::IncompleteSegment { expected, received } => write!(f, "Video segment has {received} of {expected} expected frames"),
        }
    }
}

impl std::error::Error for VideoTimingError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VideoTimeline {
    timestamps_us: Vec<i64>,
}

impl VideoTimeline {
    pub fn new(mut decode_order_us: Vec<i64>) -> Result<Self, VideoTimingError> {
        decode_order_us.sort_unstable();
        let first = *decode_order_us.first().ok_or(VideoTimingError::EmptyTimeline)?;
        if first < 0 {
            return Err(VideoTimingError::NegativePts(first));
        }
        for pair in decode_order_us.windows(2) {
            if pair[0] == pair[1] {
                return Err(VideoTimingError::DuplicatePts(pair[0]));
            }
        }
        Ok(Self { timestamps_us: decode_order_us })
    }

    pub fn timestamps_us(&self) -> &[i64] {
        &self.timestamps_us
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SegmentRange {
    pub first_frame: usize,
    pub frame_count: usize,
}

impl SegmentRange {
    fn checked_range(self, total_frames: usize) -> Option<Range<usize>> {
        let end = self.first_frame.checked_add(self.frame_count)?;
        (self.frame_count > 0 && end <= total_frames).then_some(self.first_frame..end)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SegmentPlan {
    total_frames: usize,
    frames_per_segment: usize,
}

impl SegmentPlan {
    pub fn new(total_frames: usize, frames_per_segment: usize) -> Result<Self, VideoTimingError> {
        if total_frames == 0 || frames_per_segment == 0 {
            return Err(VideoTimingError::EmptyPlan);
        }
        Ok(Self { total_frames, frames_per_segment })
    }

    pub fn segment_count(&self) -> usize {
        self.total_frames.div_ceil(self.frames_per_segment)
    }

    pub fn segment(&self, index: usize) -> Option<SegmentRange> {
        if index >= self.segment_count() {
            return None;
        }
        let first_frame = index * self.frames_per_segment;
        let frame_count = (self.total_frames - first_frame).min(self.frames_per_segment);
        Some(SegmentRange { first_frame, frame_count })
    }
}

pub fn check_segment_coverage(segments: &[SegmentRange], total_frames: usize) -> Result<(), VideoTimingError> {
    let mut next_frame = 0;
    for segment in segments {
        let range = segment
            .checked_range(total_frames)
            .filter(|range| range.start == next_frame)
            .ok_or(VideoTimingError::InvalidSegmentRange(next_frame))?;
        next_frame = range.end;
    }
    if total_frames == 0 || next_frame != total_frames {
        return Err(VideoTimingError::IncompleteCoverage {
            expected: total_frames,
            covered: next_frame,
        });
    }
    Ok(())
}

pub fn validate_segment_timestamp(
    local: i64,
    previous: i64,
    first: i64,
    expected: i64,
    frame: usize,
    initial: bool,
    sync: bool,
) -> Result<(), VideoTimingError> {
    if local <= previous {
        return Err(VideoTimingError::NonmonotonicLocalPts { previous, received: local });
    }
    if initial && !sync {
        return Err(VideoTimingError::MissingInitialSync);
    }
    let actual = local.saturating_sub(first);
    if actual.abs_diff(expected) > MAX_SEGMENT_PTS_DRIFT_US {
        return Err(VideoTimingError::TimestampDrift { frame, expected, actual });
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timeline_segment_coverage_and_drift() {
        let timeline = VideoTimeline::new(vec![100_064, 300, 66_731, 25_900, 153_455]).unwrap();
        assert_eq!(timeline.timestamps_us(), [300, 25_900, 66_731, 100_064, 153_455]);
        assert!(VideoTimeline::new(vec![1, 1]).is_err());
        assert!(VideoTimeline::new(vec![1, -1]).is_err());
        assert!(VideoTimeline::new(vec![]).is_err());

        let segs = [
            SegmentRange { first_frame: 0, frame_count: 2 },
            SegmentRange { first_frame: 2, frame_count: 3 },
        ];
        assert_eq!(check_segment_coverage(&segs, 5), Ok(()));
        assert!(check_segment_coverage(&segs[..1], 5).is_err());
        assert_eq!(validate_segment_timestamp(28_623, 3_000, 3_000, 25_600, 1, false, false), Ok(()));
        assert!(validate_segment_timestamp(28_624, 3_000, 3_000, 25_600, 1, false, false).is_err());
    }
}
