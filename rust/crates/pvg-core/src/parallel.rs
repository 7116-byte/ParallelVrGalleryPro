//! Lightweight reusable row-stripe worker pool (zero external dependencies, deadlock-free).
//!
//! Uses `try_lock` on dispatch so concurrent callers (e.g. multiple image workers) never block
//! or oversubscribe cores: the first caller fans out across up to 4 threads while concurrent
//! callers execute directly on their own thread.

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Condvar, LazyLock, Mutex};
use std::thread;

const MIN_PARALLEL_PIXELS: usize = 256 * 256;
const MAX_THREADS: usize = 4;

type JobFn = unsafe fn(*const (), usize, usize);

struct Job {
    generation: usize,
    func: Option<JobFn>,
    ctx: usize,
    total_rows: usize,
    num_chunks: usize,
}

struct Pool {
    dispatch_lock: Mutex<()>,
    state: Mutex<Job>,
    wake_workers: Condvar,
    remaining: AtomicUsize,
    done_lock: Mutex<()>,
    wake_caller: Condvar,
    num_threads: usize,
}

static POOL: LazyLock<&'static Pool> = LazyLock::new(|| {
    let available = thread::available_parallelism().map_or(1, usize::from);
    let num_threads = available.clamp(1, MAX_THREADS);
    let pool: &'static Pool = Box::leak(Box::new(Pool {
        dispatch_lock: Mutex::new(()),
        state: Mutex::new(Job {
            generation: 0,
            func: None,
            ctx: 0,
            total_rows: 0,
            num_chunks: 1,
        }),
        wake_workers: Condvar::new(),
        remaining: AtomicUsize::new(0),
        done_lock: Mutex::new(()),
        wake_caller: Condvar::new(),
        num_threads,
    }));
    for worker_id in 1..num_threads {
        let _ = thread::Builder::new()
            .name(format!("pvg-row-{worker_id}"))
            .spawn(move || worker_loop(pool, worker_id));
    }
    pool
});

fn worker_loop(pool: &'static Pool, worker_id: usize) {
    let mut seen_gen = 0usize;
    loop {
        let (func, ctx, start, end) = {
            let mut job = pool.state.lock().unwrap_or_else(|e| e.into_inner());
            while job.generation == seen_gen {
                job = pool.wake_workers.wait(job).unwrap_or_else(|e| e.into_inner());
            }
            seen_gen = job.generation;
            let start = job.total_rows * worker_id / job.num_chunks;
            let end = job.total_rows * (worker_id + 1) / job.num_chunks;
            (job.func, job.ctx, start, end)
        };
        if let Some(f) = func
            && start < end
        {
            let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| unsafe {
                f(ctx as *const (), start, end);
            }));
        }
        if pool.remaining.fetch_sub(1, Ordering::AcqRel) == 1 {
            let _guard = pool.done_lock.lock().unwrap_or_else(|e| e.into_inner());
            pool.wake_caller.notify_one();
        }
    }
}

/// Execute `f(start_row, end_row)` across contiguous horizontal row stripes covering `0..rows`.
/// Each stripe is guaranteed disjoint; caller ensures `f` only mutates its assigned row range.
pub fn for_each_row_stripe<F>(width: usize, rows: usize, f: F)
where
    F: Fn(usize, usize) + Sync,
{
    if rows < 8 || width.saturating_mul(rows) < MIN_PARALLEL_PIXELS {
        f(0, rows);
        return;
    }
    let pool = *POOL;
    if pool.num_threads <= 1 {
        f(0, rows);
        return;
    }
    let Ok(_dispatch_guard) = pool.dispatch_lock.try_lock() else {
        // Another caller is already utilizing the pool; execute locally to avoid contention.
        f(0, rows);
        return;
    };

    unsafe fn trampoline<F: Fn(usize, usize) + Sync>(ctx: *const (), start: usize, end: usize) {
        let closure = unsafe { &*(ctx as *const F) };
        closure(start, end);
    }

    let num_chunks = pool.num_threads.min(rows);
    let helper_count = num_chunks - 1;
    pool.remaining.store(helper_count, Ordering::Release);
    {
        let mut job = pool.state.lock().unwrap_or_else(|e| e.into_inner());
        job.func = Some(trampoline::<F>);
        job.ctx = (&f as *const F) as usize;
        job.total_rows = rows;
        job.num_chunks = num_chunks;
        job.generation = job.generation.wrapping_add(1);
        pool.wake_workers.notify_all();
    }

    // Caller executes chunk 0 on its own thread.
    let end0 = rows / num_chunks;
    f(0, end0);

    // Wait for all helper threads before returning so borrowed stack data stays valid.
    if pool.remaining.load(Ordering::Acquire) != 0 {
        let mut guard = pool.done_lock.lock().unwrap_or_else(|e| e.into_inner());
        while pool.remaining.load(Ordering::Acquire) != 0 {
            guard = pool.wake_caller.wait(guard).unwrap_or_else(|e| e.into_inner());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::for_each_row_stripe;
    use std::sync::atomic::{AtomicUsize, Ordering};

    #[test]
    fn covers_every_row_exactly_once() {
        let rows = 512;
        let width = 512;
        let hits: Vec<AtomicUsize> = (0..rows).map(|_| AtomicUsize::new(0)).collect();
        for_each_row_stripe(width, rows, |start, end| {
            for y in start..end {
                hits[y].fetch_add(1, Ordering::Relaxed);
            }
        });
        assert!(hits.iter().all(|h| h.load(Ordering::Relaxed) == 1));
    }
}
