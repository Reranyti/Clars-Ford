import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Swaps changed classes into a RUNNING game, without a restart (the same thing an IDE's "reload changed classes" does).
 *
 * <p>The game must have been started with the debug agent bound to this computer only:
 * {@code -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005} (the build does this with -Photswap).
 *
 * <p>Usage: {@code java HotSwap.java <classes folder> <state file> <since, epoch millis> [port]}.
 * Only class files newer than the last swap (or than {@code since} the first time) are looked at.
 * What can change: the bodies of methods. A new field or method in an already loaded class is refused by the JVM, and the
 * class is listed so that the game can be restarted for it. A class that is not loaded yet needs nothing: it is read fresh
 * from disk the first time it is used.
 *
 * <p>Exit code: 0 all fine, 1 some classes need a restart, 2 no game to talk to.
 */
public class HotSwap {
    public static void main(String[] a) throws Exception {
        Path classes = Path.of(a[0]), state = Path.of(a[1]);
        long since = Long.parseLong(a[2]);
        int port = a.length > 3 ? Integer.parseInt(a[3]) : 5005;
        long last = since;
        if (Files.exists(state)) last = Math.max(last, Long.parseLong(Files.readString(state).trim()));

        List<Path> changed = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".class"))::iterator) {
                if (p.toFile().lastModified() > last) changed.add(p);
            }
        }
        if (changed.isEmpty()) {
            System.out.println("Изменённых классов нет.");
            return;
        }
        VirtualMachine vm;
        try {
            vm = attach(port);
        } catch (IOException e) {
            System.out.println("Игра не слушает порт " + port + ": запусти её кнопкой «с горячей заменой» (" + e.getMessage() + ").");
            System.exit(2);
            return;
        }
        if (!vm.canRedefineClasses()) {
            System.out.println("Эта Java не умеет подменять классы на ходу.");
            vm.dispose();
            System.exit(2);
            return;
        }
        int swapped = 0, fresh = 0;
        List<String> needRestart = new ArrayList<>();
        for (Path p : changed) {
            String rel = classes.relativize(p).toString().replace('\\', '.').replace('/', '.');
            String name = rel.substring(0, rel.length() - ".class".length());
            List<ReferenceType> loaded = vm.classesByName(name);
            if (loaded.isEmpty()) {
                fresh++;                                   // not loaded yet: the new file is simply used when it is first needed
                continue;
            }
            try {
                vm.redefineClasses(Map.of(loaded.get(0), Files.readAllBytes(p)));
                swapped++;
                System.out.println("заменён: " + name);
            } catch (UnsupportedOperationException e) {
                needRestart.add(name + " (изменилась структура: поля, методы или родитель)");
            } catch (Throwable e) {
                needRestart.add(name + ": " + e);
            }
        }
        vm.dispose();                                      // detaches; the game goes on
        Files.createDirectories(state.toAbsolutePath().getParent());
        Files.writeString(state, Long.toString(System.currentTimeMillis()));
        System.out.println("Итог: заменено на ходу " + swapped + ", ещё не загружено " + fresh + " (подхватятся сами), нужен перезапуск " + needRestart.size() + ".");
        for (String s : needRestart) System.out.println("  перезапуск нужен: " + s);
        System.exit(needRestart.isEmpty() ? 0 : 1);
    }

    private static VirtualMachine attach(int port) throws Exception {
        AttachingConnector socket = null;
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (c.transport().name().equals("dt_socket")) socket = c;
        }
        if (socket == null) throw new IOException("нет коннектора dt_socket");
        Map<String, Connector.Argument> args = socket.defaultArguments();
        args.get("hostname").setValue("127.0.0.1");
        args.get("port").setValue(String.valueOf(port));
        return socket.attach(args);
    }
}
