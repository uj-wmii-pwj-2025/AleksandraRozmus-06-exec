package uj.wmii.pwj.exec;

import java.util.*;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {

    private final BlockingQueue<Runnable> queue;
    private Thread thread;
    private volatile boolean isShutdown;
    private volatile boolean isTerminated;

    private MyExecService() {
        queue = new LinkedBlockingQueue<>();
        isShutdown = false;
        isTerminated = false;
        initThread();
    }

    private void initThread() {
        thread = new Thread(() -> {
            try {
                while (true) {
                    if (isShutdown && queue.isEmpty()) 
                        break;

                    Runnable task;
                    try {
                        task = queue.take();
                        task.run();
                    } catch (InterruptedException e) {
                        if (isShutdown) 
                            break;
                    }
                }
            } finally {
                isTerminated = true;
            }
        });

        thread.start();
    }

    public static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        thread.interrupt();
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        thread.interrupt();

        List<Runnable> unfinished = new ArrayList<>();
        queue.drainTo(unfinished);
        return unfinished;
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public boolean isTerminated() {
        return isTerminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        thread.join(unit.toMillis(timeout));
        return isTerminated;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (isShutdown) 
            throw new RejectedExecutionException();

        if (task == null) 
            throw new NullPointerException();

        FutureTask<T> future = new FutureTask<>(task);
        queue.offer(future);
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (isShutdown) 
            throw new RejectedExecutionException();

        if (task == null) 
            throw new NullPointerException();

        FutureTask<T> future = new FutureTask<>(task, result);
        queue.offer(future);
        return future;
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) 
            throw new NullPointerException();

        List<Future<T>> futures = new ArrayList<>();

        try {
            for (Callable<T> task : tasks) 
                futures.add(submit(task));

            for (Future<T> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {}
            }
            return futures;

        } catch (InterruptedException e) {
            for (Future<?> f : futures) 
                f.cancel(true);

            throw e;
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || unit == null) 
            throw new NullPointerException();

        long timeLimit = System.nanoTime() + unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (Callable<T> task : tasks) 
                futures.add(submit(task));

            for (Future<T> f : futures) {
                long timeLeft = timeLimit - System.nanoTime();

                if (timeLeft <= 0) 
                    break;

                try {
                    f.get(timeLeft, TimeUnit.NANOSECONDS);
                } catch (ExecutionException | TimeoutException e) {}
            }
        } catch (InterruptedException e) {
            throw e;
        }
        finally {
            for (Future<?> f : futures) {
                if (!f.isDone()) 
                    f.cancel(true);
            }
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (tasks == null) 
            throw new NullPointerException();

        if (tasks.isEmpty()) 
            throw new IllegalArgumentException();

        List<Future<T>> futures = new ArrayList<>();
        ExecutionException latest = null;

        try {
            for (Callable<T> task : tasks)
                futures.add(submit(task));

            for (Future<T> f : futures) {
                try {
                    return f.get();
                } catch (ExecutionException e) {
                    latest = e;
                }
            }

            if (latest != null) 
                throw latest;
            else 
                throw new ExecutionException(null);

        } finally {
            for (Future<?> f : futures) 
                f.cancel(true);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null || unit == null) 
            throw new NullPointerException();

        if (tasks.isEmpty()) 
            throw new IllegalArgumentException();

        List<Future<T>> futures = new ArrayList<>();
        long timeLimit = System.nanoTime() + unit.toNanos(timeout);
        ExecutionException latest = null;
        
        try {
            for (Callable<T> task : tasks) 
                futures.add(submit(task));

            for (Future<T> f : futures) {
                long timeLeft = timeLimit - System.nanoTime();

                if (timeLeft <= 0) 
                    throw new TimeoutException();

                try {
                    return f.get(timeLeft, TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    latest = e;
                } catch (TimeoutException e) {
                    throw e;
                }
            }
            if (latest != null) 
                throw latest;
            else 
                throw new ExecutionException(null);

        } finally {
            for (Future<?> f : futures) 
                f.cancel(true);
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) 
            throw new RejectedExecutionException();

        if (command == null) 
            throw new NullPointerException();

        queue.offer(command);
    }
}
