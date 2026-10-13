import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/**
 * Renders one full-screen fragment shader to a picture without starting the game, to look at it:
 * {@code java ShaderStill.java <shader.fsh> <out.png> <width> <height> <seconds> [name=value ...]}.
 * A name with one value sets a float uniform ({@code uSpread=0.9}); {@code uCurl=0.1,0.2,...} sets a float array.
 * (A tool for the author's own review, not part of the mod.)
 */
public final class ShaderStill {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(new File(args[0]).toPath());
        int w = Integer.parseInt(args[2]), h = Integer.parseInt(args[3]);
        float time = Float.parseFloat(args[4]);
        if (!GLFW.glfwInit()) throw new IllegalStateException("no GLFW");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long win = GLFW.glfwCreateWindow(64, 64, "still", 0, 0);
        if (win == 0) throw new IllegalStateException("no window");
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();
        System.out.println("GL " + GL11.glGetString(GL11.GL_VERSION) + " on " + GL11.glGetString(GL11.GL_RENDERER));

        String vs = "#version 150\nout vec2 uv;\nvoid main(){ vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)); uv = vec2(p.x, 1.0 - p.y); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
        int v = shader(GL20.GL_VERTEX_SHADER, vs);
        int f = shader(GL20.GL_FRAGMENT_SHADER, source);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, v);
        GL20.glAttachShader(prog, f);
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(prog));
        GL20.glUseProgram(prog);

        int fbo = GL30.glGenFramebuffers();
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GL11.glViewport(0, 0, w, h);

        GL20.glUniform1f(GL20.glGetUniformLocation(prog, "uTime"), time);
        GL20.glUniform2f(GL20.glGetUniformLocation(prog, "uRes"), w, h);
        for (int i = 5; i < args.length; i++) {
            String[] kv = args[i].split("=");
            String[] vals = kv[1].split(",");
            int loc = GL20.glGetUniformLocation(prog, vals.length > 1 ? kv[0] + "[0]" : kv[0]);
            if (loc < 0) {
                System.out.println("(no uniform " + kv[0] + ")");
                continue;
            }
            if (vals.length == 1 && !kv[0].equals("uLift") && !kv[0].equals("uCurl")) {
                GL20.glUniform1f(loc, Float.parseFloat(vals[0]));
            } else {
                float[] a = new float[vals.length];
                for (int k = 0; k < a.length; k++) a[k] = Float.parseFloat(vals[k]);
                GL20.glUniform1fv(loc, a);
            }
        }
        GL30.glBindVertexArray(GL30.glGenVertexArrays());
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL11.glFinish();
        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((buf.get(i) & 255) << 16) | ((buf.get(i + 1) & 255) << 8) | (buf.get(i + 2) & 255));
            }
        }
        javax.imageio.ImageIO.write(img, "png", new File(args[1]));
        System.out.println("saved " + args[1]);
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
    }

    private static int shader(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(s));
        return s;
    }
}
