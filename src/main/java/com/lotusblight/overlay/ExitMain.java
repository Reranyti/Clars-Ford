package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The exit, from the error dialog to the fight. Windows are minimized first (and brought back however
 * the process ends) so the desktop is in view, it is looked at once for the icons, and then ExitScene
 * plays over it; when the scene is done the fight (GameMain) starts in this same process. Esc or
 * Alt+F4 end it at any point.
 *
 * Usage: java -cp lotusblight.jar com.lotusblight.overlay.ExitMain [--jar lotusblight-VERSION.jar]
 *        [--song1 a.wav] [--song2 b.wav] [--song3 d.wav] [--contact c.wav] [--frames DIR --animAudio a.wav --animFps N]
 *        [--video finale.mp4 | --video-resource /path/in/jar] [--from STAGE] [--no-minimize] [--no-fight]
 *        [--cleanup]
 */
public final class ExitMain {
    private static final String SKIN = "/assets/lotusblight/textures/overlay/glitcher.png";
    /** How long the fight waits for its tracks if they are still being written. */
    private static final long SONG_WAIT_MS = 15_000;

    private ExitMain() {}

    public static void main(String[] args) throws Exception {
        String jar = "lotusblight.jar";
        String song1 = null, song2 = null, song3 = null, contact = null, interlude = null, from = null, video = null;
        List<String> animArgs = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--jar" -> jar = args[i + 1];
                case "--song1" -> song1 = args[i + 1];
                case "--song2" -> song2 = args[i + 1];
                case "--song3" -> song3 = args[i + 1];
                case "--from" -> from = args[i + 1];
                case "--video" -> video = args[i + 1];
                case "--video-resource" -> animArgs.addAll(List.of(args[i], args[i + 1]));
                case "--contact" -> { contact = args[i + 1]; animArgs.addAll(List.of(args[i], args[i + 1])); }
                case "--interlude" -> { interlude = args[i + 1]; animArgs.addAll(List.of(args[i], args[i + 1])); }
                // The finale animation for the fight, passed through as is.
                case "--frames", "--animAudio", "--animFps" -> animArgs.addAll(List.of(args[i], args[i + 1]));
                default -> {}
            }
        }
        List<String> flags = List.of(args);
        boolean minimize = !flags.contains("--no-minimize") && isWindows();
        boolean fight = !flags.contains("--no-fight");
        boolean cleanup = flags.contains("--cleanup");
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen to draw on.");
            System.exit(2);
        }
        // Pictures are read straight from memory - no temporary cache files on disk.
        ImageIO.setUseCache(false);
        int[] skin = loadSkin();
        String body = "Не найден файл Glitcher_.jar\n"
                + "По пути: .minecraft\\mods\\" + jar + "\\character\\Glitcher_Architect.jar";

        if (minimize) {
            shell("MinimizeAll()");
            // Whatever way the process ends, the windows come back.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shell("UndoMinimizeALL()")));
            // Give the minimize animation time to finish before the desktop is looked at.
            Thread.sleep(900);
        }
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int floorY = screen.height - insets.bottom;
        // Looked at before our window exists, so the picture is of the desktop and not of us.
        DesktopSnapshot desktop = DesktopSnapshot.capture(screen, floorY);

        List<String> fightArgs = new ArrayList<>();
        if (song1 != null) fightArgs.addAll(List.of("--song1", song1));
        if (song2 != null) fightArgs.addAll(List.of("--song2", song2));
        if (song3 != null) fightArgs.addAll(List.of("--song3", song3));
        fightArgs.addAll(animArgs);
        // The finale animation is played straight from the video, in memory, by the fight itself.
        if (video != null) fightArgs.addAll(List.of("--video", video));
        // Single flags go last, after the pairs.
        if (flags.contains("--chromo")) fightArgs.add("--chromo");
        if (flags.contains("--video-delete")) fightArgs.add("--video-delete");

        if (cleanup) {
            fightArgs.add("--from-game");          // started by the game: the closing scene gives its window back
            // Run from the mod: our own temporary files go once the process ends, whichever way.
            List<File> ours = new ArrayList<>();
            for (String path : new String[]{song1, song2, song3, contact, interlude}) if (path != null) ours.add(new File(path));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // The paths arrived as arguments: only a known track in our own temp folder is ever removed.
                for (File f : ours) com.lotusblight.safety.SafeFiles.deleteOwn(f.toPath());
            }));
        }
        ExitScene.Stage start = from == null ? null : ExitScene.Stage.valueOf(from.toUpperCase(Locale.ROOT));
        ExitScene scene = new ExitScene(screen.width, screen.height, floorY, skin, desktop, body,
                () -> Toolkit.getDefaultToolkit().beep());
        if (start != null) scene.jumpTo(start);
        // The icons that fell in the scene stay gone through the fight.
        GameMain.keepIconsHidden(desktop);
        SwingUtilities.invokeLater(() -> show(screen, scene, fight ? fightArgs : null));
    }

    private static void show(Rectangle screen, ExitScene scene, List<String> fightArgs) {
        JFrame frame = new JFrame("Ошибка");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JComponent canvas = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, getWidth(), getHeight());
                scene.render(g);
                g.dispose();
            }
        };
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                scene.press(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                scene.drag(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                scene.release();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
            }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        if (scene.stage() == ExitScene.Stage.ERROR) Toolkit.getDefaultToolkit().beep();

        new Timer(1000 / ExitScene.FPS, e -> {
            scene.tick();
            if (scene.done()) {
                ((Timer) e.getSource()).stop();
                frame.dispose();
                if (fightArgs == null) System.exit(0);
                startFight(fightArgs);
                return;
            }
            canvas.repaint();
        }).start();
    }

    /** Hands over to the fight, once its tracks are on disk. */
    private static void startFight(List<String> fightArgs) {
        Thread fight = new Thread(() -> {
            try {
                long until = System.currentTimeMillis() + SONG_WAIT_MS;
                for (int i = 0; i + 1 < fightArgs.size(); i += 2) {
                    if (!fightArgs.get(i).startsWith("--song") && !fightArgs.get(i).equals("--video") && !fightArgs.get(i).equals("--contact") && !fightArgs.get(i).equals("--interlude")) continue;
                    File song = new File(fightArgs.get(i + 1));
                    while (!song.isFile() && System.currentTimeMillis() < until) Thread.sleep(100);
                }
                GameMain.main(fightArgs.toArray(new String[0]));
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        }, "fight");
        fight.start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Calls one method of Explorer's Shell.Application object through PowerShell and waits for it. */
    private static void shell(String call) {
        try {
            Process p = new ProcessBuilder(WinTools.powershell(), "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object -ComObject Shell.Application)." + call)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Not being able to minimize only means the windows stay where they are.
        }
    }

    private static int[] loadSkin() throws IOException {
        try (InputStream in = ExitMain.class.getResourceAsStream(SKIN)) {
            if (in == null) throw new IOException("missing " + SKIN);
            BufferedImage image = ImageIO.read(in);
            return image.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}
