package com.lotusblight.overlay;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.awt.image.BufferedImage;

/**
 * The whole boss encounter, end to end: song 1 over the desktop, then the finale animation, then song 2
 * over it, then a results screen ranking how well both songs went. The Glitcher is the boss - hits drain
 * his HP; misses drain the player's. Losing a song ends the run (the real build makes that a crash; here
 * it just shows the defeat screen). A test harness driving the pieces together.
 */
public final class GameMain {
    private static final int FPS = 60;
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);

    private enum Phase { SONG1, LESSON, SONG2, INTERLUDE, SONG3, RESULTS, FINALE, DEFEAT }

    /** Icons taken off the desktop before the fight (by the exit scene), kept covered while it runs. */
    private static volatile DesktopSnapshot hiddenIcons;

    /** The pictures of the desktop's icons (from the screen shot taken before the fight), or none. */
    static java.util.List<java.awt.image.BufferedImage> desktopIcons() {
        DesktopSnapshot d = hiddenIcons;
        java.util.List<java.awt.image.BufferedImage> list = new java.util.ArrayList<>();
        if (d != null) for (DesktopSnapshot.Icon icon : d.icons) list.add(icon.image);
        return list;
    }

    static java.util.List<java.awt.Rectangle> desktopIconSpots() {
        DesktopSnapshot d = hiddenIcons;
        java.util.List<java.awt.Rectangle> list = new java.util.ArrayList<>();
        if (d != null) for (DesktopSnapshot.Icon icon : d.icons) list.add(icon.bounds);
        return list;
    }

    /** He picked this one up: the overlay paints bare wallpaper over it (nothing on the real desktop moves). */
    static void takeDesktopIcon(int index) {
        DesktopSnapshot d = hiddenIcons;
        if (d != null && index >= 0 && index < d.icons.size()) d.icons.get(index).taken = true;
    }

    static void keepIconsHidden(DesktopSnapshot desktop) {
        hiddenIcons = desktop;
    }
    // The finale animation plays as the song-2 backdrop from 2:15 (the video is that 2:15-3:04 window).
    private static final double ANIM_START_MS = 135_000;

    // Set with --record FILE: instead of the eye mechanic, every space-bar press is written to FILE with the
    // song clock, so the moments for the eye can be taken from someone tapping the beat.
    private static File recordFile;
    // Set with --contact WAV: the track that plays while the eye mechanic runs (the song itself is stopped then).
    private static String contactWav;
    // Set with --from-game: the process was started by the game itself, whose window is ours to give back.
    private static boolean fromGame;
    // Set with --interlude WAV: the music of the cutscene between the second and third songs.
    private static String interludeWav;
    // A picture of the desktop for the shattering when the fight was started on its own (no exit scene took one).
    /** Health for the first song on the Chromo map - the map is very hard, so a lot of misses are allowed. */
    private static final int CHROMO_SONG1_HP = 320;
    private static java.awt.Robot cursorRobot;
    private static java.awt.image.BufferedImage testShot;

    static java.awt.image.BufferedImage desktopShot() {
        DesktopSnapshot d = hiddenIcons;
        return d != null && d.shot != null ? d.shot : testShot;
    }

    /**
     * The end of the closing scene. The windows come back, and the game's own window is brought to the front
     * if it was hidden; if the game is not there any more, the process simply ends. The exit code tells the
     * game the scene played out (it then hands over the horn).
     */
    private static void leaveAfterFinale() {
        leave(Finale3.EXIT_CODE);
    }

    /** Gives the windows and the game's window back and ends the process with {@code code}. */
    private static void leave(int code) {
        if (fromGame && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            shellCall("UndoMinimizeALL()");
            ProcessHandle.current().parent().filter(ProcessHandle::isAlive).ifPresent(parent -> {
                String script = "$s='[DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr h,int c);"
                        + "[DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr h);';"
                        + "Add-Type -MemberDefinition $s -Name W -Namespace N;"
                        + "$p=Get-Process -Id " + parent.pid() + " -ErrorAction SilentlyContinue;"
                        + "if($p -and $p.MainWindowHandle -ne 0){[N.W]::ShowWindow($p.MainWindowHandle,9)|Out-Null;[N.W]::SetForegroundWindow($p.MainWindowHandle)|Out-Null}";
                try {
                    Process p = new ProcessBuilder(WinTools.powershell(), "-NoProfile", "-NonInteractive", "-Command", script)
                            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                    if (!p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
                } catch (Exception ignored) { }
            });
        }
        System.exit(code);
    }

    private static void shellCall(String call) {
        try {
            Process p = new ProcessBuilder(WinTools.powershell(), "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object -ComObject Shell.Application)." + call)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (Exception ignored) { }
    }

    private static int[] loadSkinFile(String resource) {
        try (InputStream in = GameMain.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            BufferedImage img = javax.imageio.ImageIO.read(in);
            return img.getRGB(0, 0, 64, 64, null, 0, 64);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        String song1Wav = null, song2Wav = null, song3Wav = null, framesDir = null, animWav = null, video = null, videoResource = null;
        double animFps = 30, startSong2 = -1, startSong3 = -1, cutsceneAt = -1, finaleAt = -1;
        boolean minimize = false, lessonOnly = false, chromo = false;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--song1")) song1Wav = args[i + 1];
            if (args[i].equals("--song2")) song2Wav = args[i + 1];
            if (args[i].equals("--song3")) song3Wav = args[i + 1];
            if (args[i].equals("--start3")) startSong3 = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--cutscene")) cutsceneAt = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--finale")) finaleAt = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--frames")) framesDir = args[i + 1];
            if (args[i].equals("--video")) video = args[i + 1];
            if (args[i].equals("--video-resource")) videoResource = args[i + 1];
            if (args[i].equals("--animAudio")) animWav = args[i + 1];
            if (args[i].equals("--animFps")) animFps = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--start2")) startSong2 = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--record")) recordFile = new File(args[i + 1]);
            if (args[i].equals("--contact")) contactWav = args[i + 1];
            if (args[i].equals("--interlude")) interludeWav = args[i + 1];
        }
        for (String arg : args) {
            if (arg.equals("--minimize")) minimize = true;
            if (arg.equals("--from-game")) fromGame = true;
            if (arg.equals("--lesson")) lessonOnly = true;
            if (arg.equals("--chromo")) chromo = true;
        }
        if (minimize && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            // Windows out of the way for a test run started on its own (the exit scene does this in the real chain).
            shellCall("MinimizeAll()");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shellCall("UndoMinimizeALL()")));
            Thread.sleep(900);
        }
        if (recordFile != null) {
            // A developer test option: it may only touch a .txt file inside a "song-test" folder, never an arbitrary path.
            if (!com.lotusblight.safety.SafeFiles.isTestRecordFile(recordFile.toPath())) {
                System.err.println("--record refused: " + recordFile + " is not a test file");
                System.exit(2);
            }
            java.nio.file.Files.deleteIfExists(recordFile.toPath());
        }
        if (GraphicsEnvironment.isHeadless()) { System.err.println("No screen."); System.exit(2); }
        // Pictures are read straight from memory - no temporary cache files on disk.
        javax.imageio.ImageIO.setUseCache(false);

        int[] skin = loadSkin();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        // The Chromo difficulty plays the first two songs on their own, harder maps.
        OsuMap map1 = OsuMap.load("/assets/lotusblight/overlay/map_1" + (chromo ? "_chromo" : "") + ".osu");
        OsuMap map2 = OsuMap.load("/assets/lotusblight/overlay/map_2" + (chromo ? "_chromo" : "") + ".osu");
        // 615 notes at 16 stars: the first Chromo song is kind to the player's health (a miss costs one point either way).
        RhythmGame g1 = new RhythmGame(map1, chromo ? CHROMO_SONG1_HP : 48);
        RhythmGame g2 = new RhythmGame(map2, 260);
        g1.setFx(chromo ? PlayfieldFx.song1Chromo() : PlayfieldFx.song1());
        g2.setFx(PlayfieldFx.song2());
        RhythmGame g3 = null;
        if (song3Wav != null && OsuMap.class.getResource("/assets/lotusblight/overlay/map_3.osu") != null) {
            g3 = new RhythmGame(OsuMap.load("/assets/lotusblight/overlay/map_3.osu"), 300);
            g3.fullHud();
            g3.setFx(PlayfieldFx.song3(GameMain::desktopShot));
        }
        Hazards hazards = new Hazards(skin, screen.height);
        // The animation: straight from the video (a file, or inside our own jar), else from a folder of frames.
        deleteVideo = java.util.List.of(args).contains("--video-delete");
        FinaleVideo anim = video != null || videoResource != null ? openVideo(video, videoResource)
                : framesDir != null ? new VideoScene(new File(framesDir), animFps) : null;
        FakeWindows fakeWindows = new FakeWindows();
        Karaoke karaoke = new Karaoke();

        double[] contactBeats = ContactBreak.beatTimes();
        g2.mute(ContactBreak.START, ContactBreak.END);          // the eyes take over from the circles
        PhraseScreen phraseScreen = new PhraseScreen();
        for (double[] iv : phraseScreen.intervals()) g2.skip(iv[0], iv[1]);   // the black screens of words: nothing to hit there
        ContactBreak contact = new ContactBreak(contactBeats, g2::hurt,
                () -> g2.contactHit(1.0 / Math.max(1, contactBeats.length)));
        if ((cutsceneAt >= 0 || finaleAt >= 0) && desktopShot() == null) {
            try { testShot = new java.awt.Robot().createScreenCapture(screen); } catch (Exception ignored) { }
        }
        int[] honchoSkin = loadSkinFile("/assets/lotusblight/textures/entity/honcho.png");
        Engine engine = new Engine(g1, g2, g3, anim, fakeWindows, karaoke, contact, song1Wav, song2Wav, song3Wav, animWav);
        engine.phraseScreen = phraseScreen;
        engine.setup(skin, honchoSkin != null ? honchoSkin : skin, screen.height);
        if (chromo) engine.chromo1 = new ChromoSong1();
        if (lessonOnly) engine.jumpToLesson();
        if (startSong2 >= 0) engine.jumpToSong2(startSong2);
        if (startSong3 >= 0 && g3 != null) engine.jumpToSong3(startSong3);
        if (cutsceneAt >= 0 && g3 != null) engine.jumpToInterlude(cutsceneAt);
        if (finaleAt >= 0) engine.jumpToFinale(finaleAt);

        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            // Space, and Enter / Z / X as stand-ins for when the space bar is out of order; a held key counts once.
            private final java.util.Set<Integer> down = new java.util.HashSet<>();

            @Override public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_ESCAPE) engine.escape();
                if ((k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER || k == KeyEvent.VK_Z || k == KeyEvent.VK_X) && down.add(k)) engine.space();
            }

            @Override public void keyReleased(KeyEvent e) { down.remove(e.getKeyCode()); }
        });
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setComposite(AlphaComposite.Src);
                g.setColor(CATCH_INPUT);            // catch clicks; the desktop shows through
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                if (hiddenIcons != null) {
                    // Bare wallpaper where icons were knocked off before the fight.
                    for (DesktopSnapshot.Icon icon : hiddenIcons.icons) {
                        if (icon.taken) g.drawImage(icon.cover, icon.bounds.x, icon.bounds.y, null);
                    }
                }
                engine.render(g, getWidth(), getHeight(), hazards);
            }
        };
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!frame.isFocused()) frame.requestFocus();       // a click is also a chance to get the keyboard
                if (e.getButton() == MouseEvent.BUTTON1) engine.click(e.getX(), e.getY(), canvas.getWidth(), canvas.getHeight());
            }
        });
        // The Chromo threads are walls for the cursor: a step across one is undone unless the left button is held.
        var motion = new java.awt.event.MouseMotionAdapter() {
            private int px = -1, py = -1;
            private long warpedAt;
            private void moved(MouseEvent e) {
                engine.hover(e.getX(), e.getY());
                boolean held = (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
                // The cursor put back by the warp raises an event of its own, and on a scaled display it can land a pixel off: for a moment
                // after a warp nothing is judged, or a thread right beside it would push the cursor back forever.
                boolean settling = PauseClock.nano() - warpedAt < 80_000_000L;
                if (!settling && px >= 0 && engine.cursorBlocked(px, py, e.getX(), e.getY(), held, canvas.getWidth(), canvas.getHeight())) {
                    try {
                        java.awt.Point o = canvas.getLocationOnScreen();
                        if (cursorRobot == null) cursorRobot = new java.awt.Robot();
                        cursorRobot.mouseMove(o.x + px, o.y + py);
                        warpedAt = PauseClock.nano();
                        return;
                    } catch (Exception | Error ignored) {
                        // no Robot: the wall can't hold, so the cursor goes where it went
                    }
                }
                px = e.getX();
                py = e.getY();
            }
            void left() {
                px = -1;                      // out of the window (a second screen): the first step back in is not judged from a stale point
                py = -1;
            }
            @Override public void mouseMoved(MouseEvent e) { moved(e); }
            @Override public void mouseDragged(MouseEvent e) { moved(e); }
        };
        canvas.addMouseMotionListener(motion);
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { motion.left(); }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
        // The fight starts right after the exit scene's window is gone, from a process that is not in front, and
        // Windows then won't give the new window the keyboard (the mouse still works). Keep asking until it has
        // it; a tap of Alt is what lets such a request through.
        int[] tries = {0};
        Timer focusTimer = new Timer(300, null);
        focusTimer.addActionListener(ev -> {
            if (frame.isFocused() || ++tries[0] > 16) { focusTimer.stop(); return; }
            if (tries[0] <= 3) {
                try {
                    java.awt.Robot robot = new java.awt.Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyRelease(KeyEvent.VK_ALT);
                } catch (Exception | Error ignored) { }
            }
            frame.toFront();
            frame.requestFocus();
        });
        focusTimer.start();
        new Timer(1000 / FPS, e -> frame.repaint()).start();
    }

    /** Drives the phases and owns the music clock for each one. */
    private static final class Engine {
        PhraseScreen phraseScreen;
        private final RhythmGame g1, g2;
        private final FinaleVideo anim;
        private final FakeWindows fakeWindows;
        private final Karaoke karaoke;
        private final ContactBreak contact;
        private final RhythmGame g3;
        private final String song3Wav;
        private final GlitchBackdrop backdrop3 = new GlitchBackdrop();
        private final FallScene fall = new FallScene();
        private final Song3Show show = new Song3Show();
        private FightScene fight;
        private Interlude interlude;
        private Finale3 finale;
        private boolean song3Played;         // the results that follow the third song lead on to the closing cutscene
        private int screenHeight;
        private Clip interludeClip;
        private int[] glitcherSkin, honchoSkin;

        void setup(int[] glitcherSkin, int[] honchoSkin, int screenH) {
            this.glitcherSkin = glitcherSkin;
            this.honchoSkin = honchoSkin;
            this.fight = new FightScene(honchoSkin, glitcherSkin, screenH);
            this.screenHeight = screenH;
        }

        private void startInterludeMusic(double seconds) {
            if (interludeClip == null) return;
            gain(interludeClip, 0);
            interludeClip.setMicrosecondPosition((long) (seconds * 1_000_000));
            interludeClip.start();
        }

        private void stopInterludeMusic() {
            if (interludeClip != null) interludeClip.stop();
        }

        /** Straight into the cutscene between the second and third songs (for trying it out). */
        /** Straight into the closing cutscene (for trying it out). */
        void jumpToFinale(double seconds) {
            tutorialDone = true;
            if (clip != null) { clip.stop(); clip.close(); clip = null; }
            startFinale();
            finale.seek(seconds);
        }

        private void startFinale() {
            finale = new Finale3(glitcherSkin, honchoSkin, desktopShot(), screenHeight);
            phase = Phase.FINALE;
        }

        void jumpToInterlude(double seconds) {
            if (clip != null) { clip.stop(); clip.close(); clip = null; }   // the engine starts on the first song; it must not play under the cutscene
            phase = Phase.INTERLUDE;
            tutorialDone = true;
            interlude = new Interlude(glitcherSkin, honchoSkin, desktopShot(), fall);
            interlude.seek(seconds);
            startInterludeMusic(seconds);
        }
        // The third song opens with a lead-in of silence, its clock running below zero, so the first circles
        // (the map starts almost at once) are already on their way when the music begins.
        private static final double LEAD_MS = 2200;
        private boolean leadIn;
        private long leadStartNano;
        // The lesson, between the first and second songs: Honcho's track plays over a scene of its own.
        private boolean tutorial, tutorialDone;
        private long tutorialStartNano;
        private Clip briefClip;
        private boolean briefStarted;
        private long resumeNano = -1;
        private final ContactBreak tutor = new ContactBreak(ContactBreak.tutorialBeats(), d -> { }, () -> { });
        private java.awt.image.BufferedImage glitchBuf;
        private final String song1Wav, song2Wav, animWav;
        private Phase phase = Phase.SONG1;
        private Clip clip;
        private long phaseStartNano = PauseClock.nano();

        Engine(RhythmGame g1, RhythmGame g2, RhythmGame g3, FinaleVideo anim, FakeWindows fw, Karaoke k, ContactBreak contact, String s1, String s2, String s3, String aw) {
            this.g3 = g3; this.song3Wav = s3; this.backdrop3.setNoWall(true); this.backdrop3.setNoWindows(true);
            this.g1 = g1; this.g2 = g2; this.anim = anim; this.fakeWindows = fw; this.karaoke = k; this.contact = contact;
            this.song1Wav = s1; this.song2Wav = s2; this.animWav = aw;
            play(song1Wav);
            if (contactWav != null) {
                try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(contactWav))) {
                    briefClip = AudioSystem.getClip();
                    briefClip.open(in);
                } catch (Exception e) { briefClip = null; }
            }
            if (interludeWav != null) {
                try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(interludeWav))) {
                    interludeClip = AudioSystem.getClip();
                    interludeClip.open(in);
                } catch (Exception e) { interludeClip = null; }
            }
        }

        private static void gain(Clip c, double db) {
            try {
                javax.sound.sampled.FloatControl f = (javax.sound.sampled.FloatControl) c.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                f.setValue((float) Math.max(f.getMinimum(), Math.min(f.getMaximum(), db)));
            } catch (Exception ignored) { }
        }

        private void enterTutorial() {
            if (clip != null) clip.stop();                  // stays open, where it stopped
            tutor.setTutorial();
            tutorial = true;
            tutorialStartNano = PauseClock.nano();
            if (briefClip != null) { gain(briefClip, 0); briefClip.setFramePosition(0); briefClip.start(); }
        }

        private double tutorialMs() { return (PauseClock.nano() - tutorialStartNano) / 1e6; }

        private void stepTutorial(double tut) {
            if (briefClip != null) {
                double left = ContactBreak.TUTORIAL_LEN - tut;             // Honcho's track fades out over its last second and a half
                gain(briefClip, left < 1500 ? -60 * (1 - Math.max(0, left) / 1500.0) : 0);
            }
            if (tut >= ContactBreak.TUTORIAL_LEN) {
                tutorial = false;
                tutorialDone = true;
                if (briefClip != null) briefClip.stop();
                phase = Phase.SONG2;                                       // and now the song itself
                play(song2Wav);
            }
        }

        /** Straight into the lesson between the first and second songs (for trying it out). */
        void jumpToLesson() {
            startLesson();
        }

        /** Between the songs: Honcho's window, his track, and the eye to practise on. The second song begins when it ends. */
        private void startLesson() {
            if (clip != null) { clip.stop(); clip.close(); clip = null; }
            phase = Phase.LESSON;
            tutorialDone = false;
            enterTutorial();
        }

        /** Jump straight into song 2 at a given track time (for previewing phase two). */
        void jumpToSong2(double seconds) {
            phase = Phase.SONG2;
            tutorialDone = true;
            play(song2Wav);
            if (clip != null) clip.setMicrosecondPosition((long) (seconds * 1_000_000));
            g2.skipTo(seconds * 1000);
        }

        /** Straight into the third song at a given track time (for previewing it). */
        void jumpToSong3(double seconds) {
            phase = Phase.SONG3;
            leadIn = false;
            tutorialDone = true;
            play(song3Wav);
            if (clip != null) clip.setMicrosecondPosition((long) (seconds * 1_000_000));
            g3.skipTo(seconds * 1000);
        }

        private void play(String wav) {
            // Only the song itself: Honcho's track, opened at the start, has to survive the change of song.
            if (clip != null) { clip.stop(); clip.close(); clip = null; }
            // Without a track the clock runs on its own, so each song still starts from zero.
            phaseStartNano = PauseClock.nano();
            if (wav == null) return;
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(wav))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.start();
            } catch (Exception e) { clip = null; }
            phaseStartNano = PauseClock.nano();
        }

        private void stop() {
            if (clip != null) { clip.stop(); clip.close(); clip = null; }
            if (briefClip != null) { briefClip.stop(); briefClip.close(); briefClip = null; }
            if (interludeClip != null) { interludeClip.stop(); interludeClip.close(); interludeClip = null; }
        }

        /**
         * The track has played to its end. A song counts as over then even if its last circle sits too
         * close to the end for the usual half-second after it - the clock follows the track, and stops
         * with it.
         */
        private boolean trackOver() {
            return clip != null && clip.getFramePosition() >= clip.getFrameLength();
        }

        /** Seconds the results or the defeat screen stays up before the fight closes by itself. */
        private static final double END_SCREEN_SECONDS = 10;
        private long endedAt;

        private void end(Phase last) {
            phase = last;
            stop();
            endedAt = PauseClock.nano();
        }

        /** Seconds the results have been up (a key still held from the song must not skip them). */
        private double resultsShownFor() {
            return (PauseClock.nano() - endedAt) / 1e9;
        }

        private void closeWhenShown() {
            if (phase == Phase.RESULTS && song3Played) return;              // after the third song the results wait for the space bar
            if ((PauseClock.nano() - endedAt) / 1e9 < END_SCREEN_SECONDS) return;
            leave(0);
        }

        private double clockMs() {
            if (leadIn) return (PauseClock.nano() - leadStartNano) / 1e6 - LEAD_MS;
            return clip != null ? clip.getMicrosecondPosition() / 1000.0
                    : (PauseClock.nano() - phaseStartNano) / 1e6;
        }

        private int taps;
        private double lastTapMs = -1e9;

        // Esc in a song or the lesson stops the game and asks "close the window?". In the cutscenes there is no way out (on purpose);
        // on the results and the defeat screen it leaves, as it says there.
        private boolean paused;
        private boolean clipWas, briefWas;
        private final PauseDialog pauseDialog = new PauseDialog();

        void escape() {
            if (paused) { resumeGame(); return; }
            switch (phase) {
                case SONG1, LESSON, SONG2, SONG3 -> pauseGame();
                case RESULTS, DEFEAT -> leave(0);
                default -> { }
            }
        }

        private void pauseGame() {
            paused = true;
            PauseClock.pause();
            clipWas = clip != null && clip.isRunning();
            briefWas = briefClip != null && briefClip.isRunning();
            if (clipWas) clip.stop();
            if (briefWas) briefClip.stop();
        }

        private void resumeGame() {
            PauseClock.resume();
            if (clipWas && clip != null) clip.start();
            if (briefWas && briefClip != null) briefClip.start();
            paused = false;
        }

        void hover(int x, int y) {
            if (paused) pauseDialog.move(x, y);
        }

        void space() {
            if (paused) return;
            if (phase == Phase.INTERLUDE) { interlude.press(); return; }
            if (phase == Phase.LESSON) { tutor.press(ContactBreak.START + tutorialMs()); return; }
            if (phase == Phase.RESULTS && song3Played) { if (resultsShownFor() > 1.2) startFinale(); return; }      // on from the results to the closing scene
            if (phase != Phase.SONG2) return;
            double t = clockMs();
            if (recordFile != null) {
                recordTap(t);
                return;
            }
            contact.press(t);
        }

        /** Marking mode (--record): one more tap, in ms of the song, goes to the file. */
        private void recordTap(double t) {
            taps++;
            lastTapMs = t;
            try {
                java.nio.file.Files.writeString(recordFile.toPath(), String.format(java.util.Locale.ROOT, "%.1f%n", t),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) { }
        }

        private void recordHud(Graphics2D g, int w, int h, double t) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double since = t - lastTapMs;
            if (since >= 0 && since < 300) {
                double f = since / 300.0;
                double r = h * (0.07 + 0.12 * f);
                g.setStroke(new java.awt.BasicStroke((float) (h * 0.012 * (1 - f) + 2)));
                g.setColor(new Color(255, 255, 255, (int) (255 * (1 - f))));
                g.draw(new java.awt.geom.Ellipse2D.Double(w / 2.0 - r, h / 2.0 - r, r * 2, r * 2));
            }
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.03)));
            String s1 = "РАЗМЕТКА: жми ПРОБЕЛ или КЛИК под музыку.  Нажатий: " + taps + "   (" + String.format("%.1f", t / 1000.0) + " с)";
            int tw = g.getFontMetrics().stringWidth(s1);
            g.setColor(new Color(0, 0, 0, 190));
            g.drawString(s1, (w - tw) / 2 + 2, (int) (h * 0.9) + 2);
            g.setColor(Color.WHITE);
            g.drawString(s1, (w - tw) / 2, (int) (h * 0.9));
        }

        void click(int x, int y, int w, int h) {
            if (paused) {
                int hit = pauseDialog.hit(x, y);
                if (hit == PauseDialog.YES) leave(0);
                else if (hit == PauseDialog.NO) resumeGame();
                return;
            }
            if (recordFile != null && phase == Phase.SONG2) {
                recordTap(clockMs());
                return;
            }
            double t = clockMs();
            if (phase == Phase.INTERLUDE) interlude.click(x, y);
            else if (phase == Phase.RESULTS && song3Played) { if (resultsShownFor() > 1.2) startFinale(); }
            else if (phase == Phase.SONG1) g1.click(x, y, t, w, h);
            else if (phase == Phase.SONG3 && g3 != null) {
                g3.click(x, y, t, w, h);
            }
            else if (phase == Phase.SONG2) {
                fakeWindows.close(x, y);        // a click also clears a fake window it lands on
                g2.click(x, y, t, w, h);
            }
        }

        /** The first song's extras on the Chromo difficulty (null otherwise). */
        ChromoSong1 chromo1;

        /** A step of the cursor from (px,py) to (x,y): true if a Chromo thread stops it. */
        boolean cursorBlocked(int px, int py, int x, int y, boolean leftHeld, int w, int h) {
            return !paused && phase == Phase.SONG1 && chromo1 != null && chromo1.blocked(px, py, x, y, leftHeld, clockMs(), w, h);
        }

        private Hazards hazards;

        void render(Graphics2D g, int w, int h, Hazards hazards) {
            renderScene(g, w, h, hazards);
            if (paused) pauseDialog.render(g, w, h);
        }

        private void renderScene(Graphics2D g, int w, int h, Hazards hazards) {
            this.hazards = hazards;
            double t = clockMs();
            switch (phase) {
                case SONG1 -> {
                    g1.update(t);
                    if (chromo1 != null) {
                        final double tt = t;
                        hazards.actorSpin = ChromoSong1.glitcherSpin(t);
                        chromo1.render(g, w, h, t, gg -> {
                            hazards.renderBack(gg, w, h, tt);
                            chromo1.renderEyes(gg, w, h, tt);
                        }, gg -> g1.render(gg, w, h, tt), gg -> hazards.render(gg, w, h, tt));
                    } else {
                        hazards.renderBack(g, w, h, t);
                        g1.render(g, w, h, t);
                        hazards.render(g, w, h, t);
                    }
                    if (!g1.alive()) end(Phase.DEFEAT);
                    else if (g1.finished(t) || trackOver()) startLesson();
                }
                case LESSON -> {
                    stepTutorial(tutorialMs());
                    if (phase == Phase.LESSON) {
                        double vt = ContactBreak.START + tutorialMs();
                        contact.renderBackdrop(g, w, h, vt);
                        tutor.update(vt);
                        tutor.renderEye(g, w, h, vt);
                    }
                }
                case SONG2 -> {
                    g2.update(t);
                    if (recordFile == null) contact.update(t);
                    double gl = ContactBreak.glitchAmount(t);
                    if (gl > 0) {
                        // 1:17: the whole picture - circles, windows, bars - is drawn aside, then torn.
                        if (glitchBuf == null || glitchBuf.getWidth() != w || glitchBuf.getHeight() != h) {
                            glitchBuf = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        }
                        Graphics2D bg = glitchBuf.createGraphics();
                        bg.setComposite(AlphaComposite.Clear);
                        bg.fillRect(0, 0, w, h);
                        bg.setComposite(AlphaComposite.SrcOver);
                        drawSong2(bg, w, h, t);
                        contact.renderText(bg, w, h, t);
                        bg.dispose();
                        ContactBreak.blit(g, glitchBuf, w, h, gl, t);
                    } else {
                        contact.renderBackdrop(g, w, h, t);
                        if (recordFile == null) contact.renderEye(g, w, h, t);
                        drawSong2(g, w, h, t);
                        if (phraseScreen != null && recordFile == null) phraseScreen.render(g, w, h, t);      // the words, on black, over everything
                        if (recordFile != null) recordHud(g, w, h, t);
                    }
                    if (!g2.alive()) end(Phase.DEFEAT);
                    else if (g2.finished(t) || trackOver()) {
                        if (chromo1 == null && g3 != null && song3Wav != null) {
                            if (clip != null) { clip.stop(); clip.close(); clip = null; }
                            interlude = new Interlude(glitcherSkin, honchoSkin, desktopShot(), fall);
                            phase = Phase.INTERLUDE;
                            startInterludeMusic(0);
                        }
                        else end(Phase.RESULTS);
                    }
                }
                case INTERLUDE -> {
                    interlude.render(g, w, h);
                    if (interludeClip != null) {
                        double left = interlude.doneIn();                     // the music thins out with the last second and a half
                        gain(interludeClip, left < 1500 ? -60 * (1 - Math.max(0, left) / 1500.0) : 0);
                    }
                    if (interlude.failed()) end(Phase.DEFEAT);
                    else if (interlude.done()) {
                        stopInterludeMusic();
                        phase = Phase.SONG3;
                        leadIn = true;
                        leadStartNano = PauseClock.nano();
                    }
                }
                case SONG3 -> {
                    if (leadIn && clockMs() >= 0) {                       // the lead-in is over: now the music
                        leadIn = false;
                        play(song3Wav);
                    }
                    t = clockMs();
                    g3.update(t);
                    final double tt = t;
                    show.setGlitcher(glitcherSkin);
                    show.render(g, w, h, tt, (gg, sky) -> {
                        fall.render(gg, w, h, tt + LEAD_MS + 8000, 1.2, sky);
                        if (fight != null) fight.render(gg, w, h, tt, g3.lastHitAt(), g3.lastMissAt(), g3.combo());
                    });
                    g3.render(g, w, h, t);                                 // the events move it, hold it and change what the circles are
                    double coming = (t + LEAD_MS) / 1800.0;               // out of the dark, during the lead-in
                    if (coming < 1) {
                        g.setColor(new Color(0, 0, 0, (int) (255 * (1 - Math.max(0, coming)))));
                        g.fillRect(0, 0, w, h);
                    }
                    if (!g3.alive()) end(Phase.DEFEAT);
                    else if (!leadIn && (g3.finished(t) || trackOver())) { song3Played = true; end(Phase.RESULTS); }
                }
                case RESULTS -> { results(g, w, h); closeWhenShown(); }
                case FINALE -> {
                    finale.render(g, w, h);
                    if (finale.done()) leaveAfterFinale();
                }
                case DEFEAT -> { defeat(g, w, h); closeWhenShown(); }
            }
        }

        /** The second song's picture: the film once it starts, otherwise circles under the fake windows. */
        private void drawSong2(Graphics2D g, int w, int h, double t) {
            // From 2:15 the finale animation is the backdrop: it goes down first, the chorus
            // lyrics over it, and the circles with the HP bars on top of everything, so the fight
            // carries on over the video. Before that the circles play over the desktop, with the
            // Glitcher's fake error windows piling up on top of them.
            if (anim != null && t >= ANIM_START_MS - 1500 && anim.ready()) {
                // The film comes up under the melting backdrop a moment before 2:15, holding its first picture.
                double fadeIn = Math.min(1, (t - (ANIM_START_MS - 1500)) / 1500.0);
                java.awt.Composite keep = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fadeIn));
                anim.render(g, w, h, Math.max(0, t - ANIM_START_MS) / 1000.0);
                g.setComposite(keep);
                if (t >= ANIM_START_MS) karaoke.render(g, w, h, t / 1000.0);
                g2.render(g, w, h, t);
            } else {
                if (hazards != null) hazards.renderCarryover(g, w, h, t);
                g2.render(g, w, h, t);
                // The torn-up backdrop is the Glitcher's windows while the eyes are on; no extra ones on top.
                if (t < ContactBreak.START || t >= ANIM_START_MS - 1500) fakeWindows.render(g, w, h, t);
                if (hazards != null) hazards.renderSong2(g, w, h, t);
            }
        }

        private void results(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 210));
            g.fillRect(0, 0, w, h);
            int hits = g1.hits() + g2.hits() + (g3 != null ? g3.hits() : 0);
            int total = g1.total() + g2.total() + (g3 != null ? g3.total() : 0);
            double acc = total == 0 ? 0 : hits / (double) total;
            String grade = RhythmGame.grade(acc);
            int combo = Math.max(Math.max(g1.maxCombo(), g2.maxCombo()), g3 != null ? g3.maxCombo() : 0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            center(g, "РАНГ  " + grade, h * 0.20, h * 0.14, gradeColor(grade), w);
            center(g, String.format("Точность  %.1f%%", acc * 100), h * 0.42, h * 0.05, new Color(0xE0C0FF), w);
            center(g, "Попаданий  " + hits + " / " + total, h * 0.52, h * 0.05, new Color(0xE0C0FF), w);
            center(g, "Макс. комбо  " + combo, h * 0.60, h * 0.05, new Color(0xE0C0FF), w);
            center(g, song3Played ? "Пробел — продолжить" : "Esc — выход", h * 0.85, h * 0.035, new Color(0x9070B0), w);
        }

        private void defeat(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 220));
            g.fillRect(0, 0, w, h);
            center(g, "ПОРАЖЕНИЕ", h * 0.5, h * 0.12, new Color(0xFF4060), w);
            center(g, "Esc — выход", h * 0.7, h * 0.035, new Color(0x9070B0), w);
        }

        private static Color gradeColor(String grade) {
            return switch (grade) {
                case "S" -> new Color(0xFFD24A);
                case "A" -> new Color(0x60FF80);
                case "B" -> new Color(0x60C0FF);
                case "C" -> new Color(0xC080FF);
                default -> new Color(0xFF6060);
            };
        }

        private static void center(Graphics2D g, String s, double y, double size, Color c, int w) {
            g.setColor(c);
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) size));
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, (w - sw) / 2, (int) y);
        }
    }

    /**
     * The video player, or none if the video can't be opened (or the decoder isn't there). The video is
     * read whole into memory - from a file, or from a resource in our own jar, so nothing is copied out.
     */
    private static boolean deleteVideo;

    private static FinaleVideo openVideo(String file, String resource) {
        try {
            byte[] data;
            if (file != null) {
                data = java.nio.file.Files.readAllBytes(new File(file).toPath());
                if (deleteVideo) {                                   // it is all in memory now: the file need not stay on the disk a moment longer
                    try {
                        // Only our own video, in our own cache folder: the path came in as an argument, so it is checked, not trusted.
                        java.nio.file.Path video = new File(file).toPath();
                        com.lotusblight.safety.SafeFiles.deleteOwn(video);
                        java.nio.file.Path dir = video.toAbsolutePath().getParent();
                        if (dir != null) com.lotusblight.safety.SafeFiles.deleteEmptyCacheDir(dir);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                try (InputStream in = GameMain.class.getResourceAsStream(resource)) {
                    if (in == null) return null;
                    data = in.readAllBytes();
                }
            }
            return new VideoStream(data);
        } catch (Exception | LinkageError e) {
            System.err.println("Finale video: " + e);
            return null;
        }
    }

    private static int[] loadSkin() throws Exception {
        try (InputStream in = GameMain.class.getResourceAsStream("/assets/lotusblight/textures/overlay/glitcher.png")) {
            BufferedImage img = javax.imageio.ImageIO.read(in);
            return img.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}
