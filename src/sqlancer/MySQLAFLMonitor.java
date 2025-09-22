package sqlancer;
import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
public class MySQLAFLMonitor {
    // 常量
    private static final int AFL_MAP_SIZE = 1533718;
    private static final String AFL_SHM_ENV_VAR = "__AFL_SHM_ID";

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
    private volatile boolean running = false;
    private final byte[] coverageBuf = new byte[AFL_MAP_SIZE];

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
        initShareMemory();
        CLib.INSTANCE.setenv(AFL_SHM_ENV_VAR, String.valueOf(shmId), 1);

        System.out.println("=== MySQL AFL Coverage Monitor ===");
        System.out.println("Shared Memory ID: " + shmId);
        System.out.println("Environment Variable: " + AFL_SHM_ENV_VAR + "=" + shmId);
        System.out.println("Coverage Map Size: " + AFL_MAP_SIZE + " bytes");
        return true;
    }

    private void printUsage() {
        System.out.println("\n=== 使用说明 ===");
        System.out.println("1. 保持此监视器运行");
        System.out.println("2. 另一个终端中编译 (已打 AFL 插桩) 的 MySQL:");
        System.out.println("   export " + AFL_SHM_ENV_VAR + "=" + shmId);
        System.out.println("   export CC=afl-gcc");
        System.out.println("   export CXX=afl-g++");
        System.out.println("   cmake . -DCMAKE_C_COMPILER=afl-gcc -DCMAKE_CXX_COMPILER=afl-g++");
        System.out.println("   make");
        System.out.println("3. 或运行已插桩 mysqld:");
        System.out.println("   export " + AFL_SHM_ENV_VAR + "=" + shmId);
        System.out.println("   ./mysqld --datadir=...");
        System.out.println("4. 使用 mysql 客户端执行查询: mysql -e \"SELECT 1;\"\n");
    }

    public void startInteractive() {
        running = true;
        printUsage();
        System.out.println("Commands:");
        System.out.println("  r - 覆盖率报告");
        System.out.println("  d - Top 20 热点边");
        System.out.println("  c - 清空覆盖率");
        System.out.println("  w - 监控模式");
        System.out.println("  s - 保存覆盖率文件");
        System.out.println("  h - 帮助");
        System.out.println("  q - 退出\n");

        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        while (running) {
            System.out.print("mysql-afl> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            char cmd = line.charAt(0);
            switch (cmd) {
                case 'r' : showCoverageReport();break;
                case 'd' : showDetailedCoverage();break;
                case 'c' : clearCoverage();break;
                case 'w' : watchMode(sc);break;
                case 's' : saveCoverage();break;
                case 'h' : printUsage();break;
                case 'q' : running = false;break;
                default : System.out.println("未知命令: " + cmd);
            }
        }
        System.out.println("退出监视器");
    }

    private void refreshBuffer() {
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
            runningWatch = false;
        });
        runningWatch = true;
        inputThread.setDaemon(true);
        inputThread.start();

        while (runningWatch) {
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
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("\n退出监控模式\n");
    }

    private volatile boolean runningWatch = false;

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
            System.out.println("共享内存已清理");
        }
    }

    public void initShareMemory() {
        for (int i = 0; i < AFL_MAP_SIZE; i++) {
            shmPtr.setByte(i, (byte) 0);
        }
    }

    public static void main(String[] args) {
        MySQLAFLMonitor monitor = new MySQLAFLMonitor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown] 最终覆盖率：");
            monitor.showCoverageReport();
            monitor.cleanup();
        }));
        if (!monitor.createSharedMemory()) {
            System.err.println("初始化失败");
            return;
        }
        monitor.startInteractive();
        monitor.cleanup();
    }
}
