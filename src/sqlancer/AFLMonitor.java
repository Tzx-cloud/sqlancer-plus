package sqlancer;
import com.sun.jna.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.sleep;

public class AFLMonitor implements AutoCloseable {
    // 常量
    public static final int AFL_MAP_SIZE = 1533718;
    private static final String AFL_SHM_ENV_VAR = "__AFL_SHM_ID";
    private static final String   DBMS_PATH= "/usr/local/mysql/bin/mysqld";  // 请根据实际路径修改
    // SysV IPC 常量
    private static final int IPC_PRIVATE = 0;
    private static final int IPC_CREAT = 01000;
    private static final int IPC_RMID = 0;



    // JNA 接口
    public interface CLib extends Library {
        CLib INSTANCE = Native.load("c", CLib.class);

        int shmget(int key, int size, int shmflg);
        Pointer shmat(int shmid, Pointer shmaddr, int shmflg);
        int shmdt(Pointer shmaddr);
        int shmctl(int shmid, int cmd, Pointer buf);
        int setenv(String name, String value, int overwrite);
    }

    private int shmId = -1;
    private Pointer shmPtr = null;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean runningWatch = new AtomicBoolean(false);
    public final byte[] coverageBuf = new byte[AFL_MAP_SIZE];
    private static volatile AFLMonitor INSTANCE;
    private Process dbmsProcess = null;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public byte[] getCoverageBuf() {
        return coverageBuf;
    }

    private AFLMonitor() {
        if (!createSharedMemory()) {
            throw new IllegalStateException("共享内存初始化失败");
        }
        try {
            dbmsProcess = startDBMS();
            Thread.sleep(5000);
        } catch (Exception e) {
            cleanup();
            throw new RuntimeException(e);
        }

    }

    private void silentClose() {
        try {
            close();
        } catch (Exception ignored) {}
    }

    @Override
    public void close() throws InterruptedException {
        if (!closed.compareAndSet(false, true)) return;
        if (dbmsProcess != null && dbmsProcess.isAlive()) {
            sleep(3000);
            dbmsProcess.destroy(); // 发送 SIGTERM
            try {
                if (!dbmsProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    System.err.println("mysqld 退出超时，强制杀死");
                    dbmsProcess.destroyForcibly();
                    dbmsProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        cleanup();
    }
    public static AFLMonitor getInstance() {
        if (INSTANCE == null) {
            synchronized (AFLMonitor.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AFLMonitor();
                }
            }
        }
        return INSTANCE;
    }

    public boolean createSharedMemory() {
        shmId = CLib.INSTANCE.shmget(IPC_PRIVATE, AFL_MAP_SIZE, IPC_CREAT | 0600);
        if (shmId < 0) {
            System.err.println("创建共享内存失败");
            return false;
        }
        shmPtr = CLib.INSTANCE.shmat(shmId, Pointer.NULL, 0);
        if (Pointer.nativeValue(shmPtr) == Pointer.nativeValue(Pointer.createConstant(-1))) {
            System.err.println("附加共享内存失败");
            return false;
        }
        // 初始化置零
        clearCoverage();
        CLib.INSTANCE.setenv(AFL_SHM_ENV_VAR, String.valueOf(shmId), 1);

        System.out.println("=== MySQL AFL Coverage Monitor ===");
        System.out.println("Shared Memory ID: " + shmId);
        System.out.println("Environment Variable: " + AFL_SHM_ENV_VAR + "=" + shmId);
        System.out.println("Coverage Map Size: " + AFL_MAP_SIZE + " bytes");
        return true;
    }




    /**
     * 启动独立 mysqld 进程（AFL 插桩版）。

     */
    public Process startDBMS() throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(DBMS_PATH);
//        if (args != null && args.length > 0) {
//            cmd.addAll(java.util.Arrays.asList(args));
//        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        java.util.Map<String,String> env = pb.environment();
        env.put(AFL_SHM_ENV_VAR, String.valueOf(shmId));
        env.put("AFL_MAP_SIZE", String.valueOf(AFL_MAP_SIZE));
        env.put("AFL_IGNORE_PROBLEMS", "1");
        env.put("AFL_DEBUG", "1");

        // 可根据需要切换到数据目录:
        // pb.directory(new java.io.File("/path/to/mysql/base"))
        pb.inheritIO(); // 直接在当前控制台输出
        return pb.start();
    }

    public void refreshBuffer() {
        if (shmPtr == null) return;
        shmPtr.read(0, coverageBuf, 0, AFL_MAP_SIZE);
    }

    public void showCoverageReport() {
        refreshBuffer();
        int hitEdges = 0;
        long totalHits = 0;
        int maxHits = 0;
        int maxIdx = -1;
        for (int i = 0; i < AFL_MAP_SIZE; i++) {
            int v = coverageBuf[i] & 0xFF;
            if (v > 0) {
                hitEdges++;
                totalHits += v;
                if (v > maxHits) {
                    maxHits = v;
                    maxIdx = i;
                }
            }
        }
        System.out.println("\n=== MySQL Coverage Report ===");
        double pct = hitEdges * 100.0 / AFL_MAP_SIZE;
        System.out.printf("Hit edges:       %d/%d (%.2f%%)%n", hitEdges, AFL_MAP_SIZE, pct);
        System.out.println("Total hits:      " + totalHits);
        System.out.println("Hottest edge:    " + maxIdx + " (hit " + maxHits + " times)");
        System.out.printf("Avg hits/edge:   %.1f%n", hitEdges > 0 ? (double) totalHits / hitEdges : 0.0);
        if (hitEdges == 0) {
            System.out.println("Status:          No coverage detected");
        } else if (hitEdges < 100) {
            System.out.println("Status:          Low coverage");
        } else if (hitEdges < 1000) {
            System.out.println("Status:          Moderate coverage");
        } else {
            System.out.println("Status:          Good coverage");
        }
        System.out.println("==============================\n");
    }

    public void showDetailedCoverage() {
        refreshBuffer();
        System.out.println("\n=== Top 20 Hottest Edges ===");
        // 复制
        byte[] temp = Arrays.copyOf(coverageBuf, coverageBuf.length);
        for (int rank = 1; rank <= 20; rank++) {
            int maxHits = 0;
            int idx = -1;
            for (int i = 0; i < temp.length; i++) {
                int v = temp[i] & 0xFF;
                if (v > maxHits) {
                    maxHits = v;
                    idx = i;
                }
            }
            if (maxHits == 0) break;
            int barLen = maxHits * 20 / 255;
            StringBuilder bar = new StringBuilder("[");
            for (int i = 0; i < 20; i++) bar.append(i < barLen ? '#' : ' ');
            bar.append(']');
            System.out.printf("%2d. Edge %-6d: %4d hits %s%n", rank, idx, maxHits, bar);
            temp[idx] = 0;
        }
        System.out.println("=============================\n");
    }

    public void clearCoverage() {
        if (shmPtr == null) return;
        for (int i = 0; i < AFL_MAP_SIZE; i++) {
            shmPtr.setByte(i, (byte) 0);
        }
        System.out.println("已清空覆盖率\n");
    }

    public void watchMode(Scanner sc) {
        System.out.println("监控模式（回车退出）");
        long start = System.currentTimeMillis();
        int prevEdges = 0;
        Thread inputThread = new Thread(() -> {
            sc.nextLine();
            runningWatch.set(false);
        });
        runningWatch.set(true);
        inputThread.setDaemon(true);
        inputThread.start();

        while (runningWatch.get()) {
            refreshBuffer();
            int edges = 0;
            long hits = 0;
            for (int i = 0; i < AFL_MAP_SIZE; i++) {
                int v = coverageBuf[i] & 0xFF;
                if (v > 0) {
                    edges++;
                    hits += v;
                }
            }
            long elapsedMs = System.currentTimeMillis() - start;
            long elapsedSec = Math.max(1, elapsedMs / 1000);
            double rate = edges * 60.0 / elapsedSec;
            System.out.printf("\r[%02d:%02d] Edges: %d (+%d) | Hits: %d | Rate: %.1f edges/min   ",
                    (elapsedSec / 60), (elapsedSec % 60),
                    edges, edges - prevEdges, hits, rate);
            prevEdges = edges;
            try {
                sleep(500);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("\n退出监控模式\n");
    }

    public void saveCoverage() {
        refreshBuffer();
        String name = "mysql_coverage_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".dat";
        try (FileOutputStream fos = new FileOutputStream(name)) {
            fos.write(coverageBuf);
            System.out.println("已保存: " + name + " (" + AFL_MAP_SIZE + " bytes)\n");
        } catch (IOException e) {
            System.err.println("写入失败: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (shmPtr != null) {
            CLib.INSTANCE.shmdt(shmPtr);
            shmPtr = null;
        }
        if (shmId >= 0) {
            CLib.INSTANCE.shmctl(shmId, IPC_RMID, Pointer.NULL);
            shmId       = -1;
            System.out.println("共享内存已清理");
        }
    }

//    public static void main(String[] args) {
//        AFLMonitor monitor=AFLMonitor.getInstance();
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            System.out.println("\n[Shutdown] 最终覆盖率：");
//            monitor.showCoverageReport();
//            monitor.cleanup();
//        }));
//        if (!monitor.createSharedMemory()) {
//            System.err.println("初始化失败");
//            return;
//        }
//        monitor.startInteractive();
//        monitor.cleanup();
//    }
}
