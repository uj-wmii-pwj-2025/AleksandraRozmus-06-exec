package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
            RejectedExecutionException.class,
            () -> s.submit(new TestRunnable()));
    }


    @Test
    void testSubmitNullRunnableThrows() {
        MyExecService s = MyExecService.newInstance();
        assertThrows(NullPointerException.class, () -> s.submit((Runnable) null));
        assertThrows(NullPointerException.class, () -> s.submit((Runnable) null, "result"));
        s.shutdown();
    }


    @Test
    void testNoTasksAfterShutdown() {
        MyExecService s = MyExecService.newInstance();
        s.shutdown();
        assertThrows(RejectedExecutionException.class, () -> s.submit(new TestRunnable()));
    }

    @Test
    void testShutdownNow() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r1 = new TestRunnable();
        TestRunnable r2 = new TestRunnable();

        s.execute(r1);
        s.execute(r2);

        List<Runnable> pending = s.shutdownNow();
        assertNotNull(pending);
        assertTrue(pending.size() >= 0);
    }

    @Test
    void testIsShutdownAndIsTerminated() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();
        assertFalse(s.isShutdown());
        assertFalse(s.isTerminated());

        s.shutdown();

        assertTrue(s.isShutdown());
        s.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertTrue(s.isTerminated());
    }

    @Test
    void testShutdownNowCausesTermination() throws Exception {
        MyExecService s = MyExecService.newInstance();
        s.submit(() -> ExecServiceTest.doSleep(50));
        s.shutdownNow();

        s.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertTrue(s.isTerminated());
    }

    @Test
    void testIsTerminatedAfterFinishingTasks() throws Exception {
        MyExecService s = MyExecService.newInstance();
        s.submit(() -> ExecServiceTest.doSleep(20));
        s.shutdown();

        s.awaitTermination(200, TimeUnit.MILLISECONDS);
        assertTrue(s.isTerminated());
    }


    @Test
    void testAwaitTermination() throws Exception {
        MyExecService s = MyExecService.newInstance();
        s.submit(() -> ExecServiceTest.doSleep(100));
        s.shutdown();

        boolean result = s.awaitTermination(200, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }
    

    @Test
    void testInvokeAll() throws Exception {
        MyExecService s = MyExecService.newInstance();

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> 1);
        tasks.add(() -> 2);
        tasks.add(() -> 3);

        List<Future<Integer>> results = s.invokeAll(tasks);
        assertEquals(3, results.size());
        assertEquals(1, results.get(0).get());
        assertEquals(2, results.get(1).get());
        assertEquals(3, results.get(2).get());

        s.shutdown();
    }

    @Test
    void testInvokeAllWithTimeout() throws Exception {
        MyExecService s = MyExecService.newInstance();

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> { ExecServiceTest.doSleep(50); return 1; });
        tasks.add(() -> { ExecServiceTest.doSleep(200); return 2; });

        List<Future<Integer>> results = s.invokeAll(tasks, 100, TimeUnit.MILLISECONDS);
        assertTrue(results.get(0).isDone());
        assertTrue(results.get(1).isDone() || results.get(1).isCancelled());

        s.shutdown();
    }


    @Test
    void testInvokeAny() throws Exception {
        MyExecService s = MyExecService.newInstance();

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> { ExecServiceTest.doSleep(50); return 1; });
        tasks.add(() -> { ExecServiceTest.doSleep(10); return 2; });

        int result = s.invokeAny(tasks);
        assertTrue(result == 1 || result == 2);

        s.shutdown();
    }

    @Test
    void testInvokeAnyWithTimeout() throws Exception {
        MyExecService s = MyExecService.newInstance();

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> { ExecServiceTest.doSleep(200); return 1; });
        tasks.add(() -> { ExecServiceTest.doSleep(300); return 2; });

        assertThrows(TimeoutException.class, () -> s.invokeAny(tasks, 100, TimeUnit.MILLISECONDS));

        s.shutdown();
    }


    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

class StringCallable implements Callable<String> {

    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}
class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}
