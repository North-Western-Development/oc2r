package li.cil.oc2.common.vm.terminal;

import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import li.cil.ceres.api.Serialized;
import li.cil.oc2.common.vm.terminal.escapes.apc.APCManager;
import li.cil.oc2.common.vm.terminal.escapes.csi.CSIManager;
import li.cil.oc2.common.vm.terminal.escapes.*;
import li.cil.oc2.common.vm.terminal.escapes.dcs.DCSManager;
import li.cil.oc2.common.vm.terminal.escapes.osc.OSCManager;
import li.cil.oc2.common.vm.terminal.fonts.FontHandling;
import li.cil.oc2.common.vm.terminal.fonts.Glyph;
import li.cil.oc2.common.vm.terminal.modes.ModeState;
import li.cil.oc2.common.vm.terminal.modes.PrivateModeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Serialized
public class Terminal {
    public boolean Use1006 = false;

    // Constants
    public static final int WIDTH = 80, HEIGHT = 24;
    public static final int CHAR_WIDTH = 8;
    public static final int CHAR_HEIGHT = 16;

    public static final int STYLE_BOLD_MASK = 1;
    public static final int STYLE_DIM_MASK = 1 << 1;
    public static final int STYLE_UNDERLINE_MASK = 1 << 2;
    public static final int STYLE_BLINK_MASK = 1 << 3;
    public static final int STYLE_INVERT_MASK = 1 << 4;
    public static final int STYLE_HIDDEN_MASK = 1 << 5;
    public static final int STYLE_ITALIC_MASK = 1 << 6;

    // Default Styles
    public static final ColorData DEFAULT_BACKGROUND_COLOR = new ColorData(Color.WHITE, Color.BLACK, 0, ColorMode.DEFAULT_BACKGROUND);
    public static final ColorData DEFAULT_BRIGHT_COLORS = new ColorData(Color.WHITE, Color.BLACK, 0, ColorMode.SIXTEEN_COLOR_BRIGHT);
    public static final ColorData DEFAULT_COLORS = new ColorData(Color.WHITE, Color.BLACK, 0, ColorMode.SIXTEEN_COLOR);
    public static final byte DEFAULT_STYLE = 0;
    public static final ColorData DEFAULT_256_COLORS = new ColorData(Color.WHITE, Color.BLACK, 0, ColorMode.TWO_FIFTY_SIX_COLOR);
    public static final ColorData DEFAULT_TRUE_COLOR_FOREGROUND = new ColorData(238, 238, 238, ColorMode.TRUE_COLOR);
    public static final ColorData DEFAULT_TRUE_COLOR_BACKGROUND = new ColorData(0, 0, 0, ColorMode.TRUE_COLOR);
    public static final int TAB_WIDTH = 4;

    // Current Color Data
    public ColorMode currentForegroundColorMode = ColorMode.SIXTEEN_COLOR;
    public ColorMode currentBackgroundColorMode = ColorMode.SIXTEEN_COLOR;
    public ColorData sixteenColor;
    public ColorData sixteenColorBright;
    // 256 Color
    public ColorData twoFiftySixColor;
    // True Color
    public ColorData backgroundColor;
    public ColorData foregroundColor;
    // Style info packed into one byte for compact storage
    public byte style;

    // Rendering Data
    public final int SCROLL_BACK_COUNT = 20;
    public final ByteArrayFIFOQueue input = new ByteArrayFIFOQueue(32);
    public final int[] buffer = new int[WIDTH * HEIGHT * SCROLL_BACK_COUNT];
    public final ColorData[] colors = new ColorData[WIDTH * HEIGHT * SCROLL_BACK_COUNT];
    public final ColorData[] colorsBackground = new ColorData[WIDTH * HEIGHT * SCROLL_BACK_COUNT];
    public final byte[] styles = new byte[WIDTH * HEIGHT * SCROLL_BACK_COUNT];
    public final boolean[] tabs = new boolean[WIDTH];
    public State state = State.NORMAL;
    public int scrollFirst = 0, scrollLast = HEIGHT - 1;
    public int x, y;
    public int savedX, savedY, altSavedX, altSavedY;
    public int lastRowToDisplay = 24, lastRowToDisplayMax = 24;

    // Alt Buffer
    public final int[] altBuffer = new int[WIDTH * HEIGHT];
    public final ColorData[] altColors = new ColorData[WIDTH * HEIGHT];
    public final ColorData[] altColorsBackground = new ColorData[WIDTH * HEIGHT];
    public final byte[] altStyles = new byte[WIDTH * HEIGHT];
    public final boolean[] altTabs = new boolean[WIDTH];

    // Render Data
    public final transient Set<RendererModel> renderers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    public transient boolean displayOnly; // Set on client to not send responses to status requests.
    public transient boolean hasPendingBell;
    public boolean continuationByte;
    public int unicode;
    public int bytesRead;
    public int bytesToRead;
    public boolean useG0 = true;
    public int drawingModeG0;
    public int drawingModeG1;
    public int cursorMode;
    public ModeState currentModeState = new ModeState();
    public PrivateModeState currentPrivateModeState = new PrivateModeState();
    public PrivateModeState savePrivateModeState = new PrivateModeState();

    // Related Enums
    public enum ColorMode {
        @SerializedName("4")
        DEFAULT_BACKGROUND,
        @SerializedName("0")
        SIXTEEN_COLOR,
        @SerializedName("1")
        TWO_FIFTY_SIX_COLOR,
        @SerializedName("2")
        TRUE_COLOR,
        @SerializedName("3")
        SIXTEEN_COLOR_BRIGHT
    }

    public static final class CursorMode {
        public static final int DEFAULT = 0;    // BLOCK
        public static final int BLINK_BLOCK = 1;  // BLINKING BLOCK
        public static final int STEADY_BLOCK = 2;  // STEADY BLOCK
        public static final int BLINK_UNDERLINE = 3; // BLINKING UNDERLINE
        public static final int STEADY_UNDERLINE = 4; // STEADY UNDERLINE
        public static final int BLINKING_BAR_LINE = 5; // BLINKING BAR LINE
        public static final int STEADY_BAR_LINE = 6;   // STEADY BAR LINE
    }

    public static final class DrawingMode {
        public static final int ASCII = 0;    // BLOCK
        public static final int SPECIAL_GRAPHICS = 1;  // BLINKING BLOCK
    }

    @SuppressWarnings("unused")
    public static final class Color {
        public static final int BLACK = 0;
        public static final int RED = 1;
        public static final int GREEN = 2;
        public static final int YELLOW = 3;
        public static final int BLUE = 4;
        public static final int MAGENTA = 5;
        public static final int CYAN = 6;
        public static final int WHITE = 7;
        public static final int DEFAULT_FG = 8;
        public static final int DEFAULT_BG = 9;
    }

    public enum State { // Must be public for serialization.
        NORMAL, // Reading characters normally.
        ESCAPE, // Last character was ESC, figure out what kind next.
        SHIFT_IN_CHARACTER_SET, // Shift in character set.
        SHIFT_OUT_CHARACTER_SET, // Shift out character set.
        HASH, // Escape sequence with # intermediate.
        DCS,
        OSC,
        APC,
        CONTROL_SEQUENCE, // Know what sequence we have, now parsing it.
    }

    // Nested classes
    public static class ColorData {
        public int R;
        public int G;
        public int B;
        public ColorMode Mode;

        @SuppressWarnings("unused")
        public ColorData() {
            R = 0;
            G = 0;
            B = 0;
            ColorMode Mode = ColorMode.SIXTEEN_COLOR;
        }

        public ColorData(final int r, final int g, final int b, final ColorMode mode) {
            R = r;
            G = g;
            B = b;
            Mode = mode;
        }

        public int ToInt() {
            return (((R & 0b11111111) << 16) | ((G & 0b11111111) << 8) | (B & 0b11111111));
        }

        public ColorData Copy() {
            return new ColorData(R, G, B, Mode);
        }
    }

    // Instances
    private final CSIManager csiManager = new CSIManager(this);
    private final OSCManager oscManager = new OSCManager(this);
    private final DCSManager dcsManager = new DCSManager(this);
    private final APCManager apcManager = new APCManager(this);

    // Nested interfaces
    public interface RendererView {
        void render(final PoseStack stack, final Matrix4f projectionMatrix);
    }

    public interface RendererModel {
        AtomicInteger getDirtyMask();

        void close();
    }

    // Generic Getters
    public int getWidth() {
        return WIDTH * CHAR_WIDTH;
    }

    public int getHeight() {
        return HEIGHT * CHAR_HEIGHT;
    }

    @OnlyIn(Dist.CLIENT)
    public RendererView getRenderer() {
        final Renderer renderer = new Renderer(this);
        renderers.add(renderer);
        return renderer;
    }

    public void incrementLastLineToDisplay() {
        incrementLastLineToDisplay(false);
    }

    public void incrementLastLineToDisplay(boolean scroll) {
        if (scrollFirst != 0 || scrollLast != HEIGHT - 1) return;
        boolean originallyEqual = lastRowToDisplayMax == lastRowToDisplay;
        if (!scroll) {
            lastRowToDisplayMax = Math.min(lastRowToDisplayMax + 1, (HEIGHT * SCROLL_BACK_COUNT));
        } else if (lastRowToDisplay == lastRowToDisplayMax) {
            return;
        }

        if (originallyEqual) {
            lastRowToDisplay = lastRowToDisplayMax;
        }
        else {
            lastRowToDisplay = Math.min(lastRowToDisplay + 1, lastRowToDisplayMax);
        }

        int dirtyLinesMask = 0;
        for (int i = 0; i <= 23; i++) {
            dirtyLinesMask |= 1 << i;
        }
        final int finalDirtyLinesMask = dirtyLinesMask;
        renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(finalDirtyLinesMask, (left, right) -> left | right));
    }

    public void decrementLastLineToDisplay() {
        if (scrollFirst != 0 || scrollLast != HEIGHT - 1) return;
        lastRowToDisplay = Math.max(lastRowToDisplay - 1, 24);
        int dirtyLinesMask = 0;
        for (int i = 0; i <= 23; i++) {
            dirtyLinesMask |= 1 << i;
        }
        final int finalDirtyLinesMask = dirtyLinesMask;
        renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(finalDirtyLinesMask, (left, right) -> left | right));
    }

    // Logic

    public Terminal() {
        RIS.execute(this);
    }

    // Terminal Management
    public void clear() {
        ColorData c;
        switch (currentBackgroundColorMode) {
            case SIXTEEN_COLOR -> c = sixteenColor;
            case TWO_FIFTY_SIX_COLOR -> c = twoFiftySixColor;
            case TRUE_COLOR -> c = backgroundColor;
            case SIXTEEN_COLOR_BRIGHT -> c = sixteenColorBright;
            default -> c = Terminal.DEFAULT_BACKGROUND_COLOR;
        }
        if (currentPrivateModeState.isAltBufferEnabled()) {
            Arrays.fill(altBuffer, ' ');
            Arrays.fill(altColors, DEFAULT_COLORS.Copy());
            Arrays.fill(altColorsBackground, c.Copy());
            Arrays.fill(altStyles, DEFAULT_STYLE);
        } else {
            int startIndex = (lastRowToDisplayMax - HEIGHT) * WIDTH;
            int endIndex = startIndex + (HEIGHT * WIDTH);
            Arrays.fill(buffer, startIndex, endIndex, ' ');
            Arrays.fill(colors, startIndex, endIndex, DEFAULT_COLORS.Copy());
            Arrays.fill(colorsBackground, startIndex, endIndex, c.Copy());
            Arrays.fill(styles, startIndex, endIndex, DEFAULT_STYLE);
        }
        setCursorPos(0, 0);
        renderers.forEach(model -> model.getDirtyMask().set(-1));
    }

    public void clearAlt() {
        Arrays.fill(altBuffer, ' ');
        Arrays.fill(altColors, DEFAULT_COLORS.Copy());
        ColorData c;
        switch (currentBackgroundColorMode) {
            case SIXTEEN_COLOR -> c = sixteenColor;
            case TWO_FIFTY_SIX_COLOR -> c = twoFiftySixColor;
            case TRUE_COLOR -> c = backgroundColor;
            case SIXTEEN_COLOR_BRIGHT -> c = sixteenColorBright;
            default -> c = DEFAULT_COLORS.Copy();
        }
        Arrays.fill(altColorsBackground, c.Copy());
        Arrays.fill(altStyles, DEFAULT_STYLE);
    }

    public void clearLine(final int y) {
        clearLine(y, 0, WIDTH);
    }

    public void clearLine(final int y, final int fromIndex, final int toIndex) {
        currentForegroundColorMode = ColorMode.SIXTEEN_COLOR;
        ColorData c;
        switch (currentBackgroundColorMode) {
            case SIXTEEN_COLOR -> c = sixteenColor;
            case TWO_FIFTY_SIX_COLOR -> c = twoFiftySixColor;
            case TRUE_COLOR -> c = backgroundColor;
            case SIXTEEN_COLOR_BRIGHT -> c = sixteenColorBright;
            default -> c = Terminal.DEFAULT_BACKGROUND_COLOR;
        }
        if (currentPrivateModeState.isAltBufferEnabled()) {
            Arrays.fill(altBuffer, y * WIDTH + fromIndex, y * WIDTH + toIndex, ' ');
            Arrays.fill(altColors, y * WIDTH + fromIndex, y * WIDTH + toIndex, DEFAULT_COLORS.Copy());
            Arrays.fill(altColorsBackground, y * WIDTH + fromIndex, y * WIDTH + toIndex, c.Copy());
            Arrays.fill(altStyles, y * WIDTH + fromIndex, y * WIDTH + toIndex, DEFAULT_STYLE);
        } else {
            int correctedY = (y + (lastRowToDisplayMax - HEIGHT));
            Arrays.fill(buffer, correctedY * WIDTH + fromIndex, correctedY * WIDTH + toIndex, ' ');
            Arrays.fill(colors, correctedY * WIDTH + fromIndex, correctedY * WIDTH + toIndex, DEFAULT_COLORS.Copy());
            Arrays.fill(colorsBackground, correctedY * WIDTH + fromIndex, correctedY * WIDTH + toIndex, c.Copy());
            Arrays.fill(styles, correctedY * WIDTH + fromIndex, correctedY * WIDTH + toIndex, DEFAULT_STYLE);
        }
        renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(1 << y, (prev, next) -> prev | next));
    }

    public void setCursorPos(final int x, final int y) {
        this.x = Math.max(0, Math.min(WIDTH - 1, x));
        this.y = Math.max(0, Math.min(HEIGHT - 1, y));
    }

    public void setClampedCursorPos(final int x, final int y) {
        setCursorPos(x, Math.max(scrollFirst, Math.min(scrollLast, y)));
    }

    public void setRelativeCursorPos(final int x, final int y) {
        if (currentPrivateModeState.DECOM) {
            setCursorPos(x, Math.min(scrollFirst + y, scrollLast));
        } else {
            setCursorPos(x, y);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void setDisplayOnly(final boolean value) {
        displayOnly = value;
    }

    @OnlyIn(Dist.CLIENT)
    public void releaseRenderer(final RendererView renderer) {
        if (renderer instanceof final RendererModel rendererModel) {
            rendererModel.close();
            renderers.remove(rendererModel);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        if (hasPendingBell) {
            hasPendingBell = false;
            final Minecraft client = Minecraft.getInstance();
            client.execute(() -> client.getSoundManager().play(SimpleSoundInstance.forUI(NoteBlockInstrument.PLING.getSoundEvent(), 1)));
        }
    }

    public synchronized int readInput() {
        if (input.isEmpty()) {
            return -1;
        } else {
            return input.dequeueByte() & 0xFF;
        }
    }

    @Nullable
    public synchronized ByteBuffer getInput() {
        if (input.isEmpty()) {
            return null;
        } else {
            if (!currentPrivateModeState.isAltBufferEnabled()) lastRowToDisplay = lastRowToDisplayMax;
            int dirtyLinesMask = 0;
            for (int i = 0; i <= 23; i++) {
                dirtyLinesMask |= 1 << i;
            }
            final int finalDirtyLinesMask = dirtyLinesMask;
            renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(finalDirtyLinesMask, (left, right) -> left | right));
            final ByteBuffer buffer = ByteBuffer.allocate(input.size());
            while (!input.isEmpty()) {
                buffer.put(input.dequeueByte());
            }
            buffer.flip();
            return buffer;
        }
    }

    public synchronized void putInput(final String value) {
        putInput(ByteBuffer.wrap(value.getBytes()));
    }

    public synchronized void putInput(final ByteBuffer values) {
        while (values.hasRemaining()) {
            input.enqueue(values.get());
        }
    }

    public synchronized void putOutput(final ByteBuffer values) {
        while (values.hasRemaining()) {
            putOutput(values.get());
        }
    }

    public synchronized void putOutput(final byte value) {
        synchronized (buffer) {
            synchronized (altBuffer) {
                final char ch = (char) value;
                if (!continuationByte && (ch & (1 << 7)) != 0) {
                    continuationByte = true;
                    bytesToRead = 0;
                    bytesRead = 0;
                    unicode = 0;
                    if ((ch & (1 << 6)) != 0) {
                        bytesToRead++;
                    } else {
                        continuationByte = false;
                        return;
                    }

                    if ((ch & (1 << 5)) != 0) {
                        bytesToRead++;
                    } else {
                        unicode = (ch & 0b11111) << 6; // 2 Byte Char
                        return;
                    }

                    if ((ch & (1 << 4)) != 0) {
                        bytesToRead++;
                    } else {
                        unicode = (ch & 0b1111) << 12; // 3 Byte Char
                        return;
                    }

                    unicode = (ch & 0b111) << 18; // 4 Byte Char

                    return;
                } else if (continuationByte) {
                    if ((ch & (1 << 7)) == 0) {
                        continuationByte = false;
                        bytesToRead = 0;
                        bytesRead = 0;
                        return;
                    }

                    bytesRead++;

                    unicode |= (ch & 0b111111) << ((bytesToRead - bytesRead) * 6);

                    if (bytesToRead == bytesRead) {
                        bytesToRead = 0;
                        bytesRead = 0;
                    } else {
                        return;
                    }
                }
                switch (state) {
                    case NORMAL -> {
                        switch (value) {
                            case '\007' -> hasPendingBell = true;
                            case '\033' -> state = State.ESCAPE;
                            case '\016' -> useG0 = false; // SO
                            case '\017' -> useG0 = true; // SI

                            case (byte) '\r' /* 015 */ -> setCursorPos(0, y);
                            case (byte) '\n' /* 012 */, '\013', '\014' -> {
                                if (currentModeState.LNM) {
                                    NEL.execute(this);
                                } else {
                                    IND.execute(this);
                                }
                            }
                            case (byte) '\t' /* 011 */ -> {
                                if (x < WIDTH) {
                                    do {
                                        x++;
                                    } while (x < WIDTH && (currentPrivateModeState.isAltBufferEnabled() ? !altTabs[x] : !tabs[x]));
                                }
                            }
                            case (byte) '\b' /* 010 */ -> setCursorPos(Math.min(x, WIDTH - 1) - 1, y);

                            default -> putChar((continuationByte) ? unicode : ch);
                        }
                    }
                    case ESCAPE -> {
                        if (ch == '[') { // Control Sequence Indicator
                            csiManager.reset();
                            state = State.CONTROL_SEQUENCE;
                        } else if (ch == '(') { // SCS – Select Character Set
                            state = State.SHIFT_IN_CHARACTER_SET;
                        } else if (ch == ')') { // SCS – Select Character Set
                            state = State.SHIFT_OUT_CHARACTER_SET;
                        } else if (ch == '#') { // # Intermediate
                            state = State.HASH;
                        } else if (ch == 'P') {
                            dcsManager.reset();
                            state = State.DCS;
                        } else if (ch == ']') {
                            oscManager.reset();
                            state = State.OSC;
                        } else if (ch == '_') {
                            apcManager.reset();
                            state = State.APC;
                        } else {
                            state = State.NORMAL;
                            switch (ch) {
                                case 'D' -> IND.execute(this);   // IND – Index
                                case 'E' -> NEL.execute(this);   // NEL – Next Line
                                case 'M' -> RI.execute(this);    // RI – Reverse Index
                                case '7' -> DECSC.execute(this); // DECSC – Save Cursor (DEC public)
                                case '8' -> DECRC.execute(this); // DECRC – Restore Cursor (DEC public)
                                case 'H' -> HTS.execute(this);   // HTS – Horizontal Tabulation Set
                                case 'c' -> RIS.execute(this);   // RIS – Reset To Initial State
                                case '=' -> {
                                }      // DECKPAM – Keypad Application Mode (DEC public)
                                case '>' -> {
                                }      // DECKPNM – Keypad Numeric Mode (DEC public)
                                default -> System.out.println("Invalid escape: " + ch);
                            }
                        }
                    }
                    case CONTROL_SEQUENCE -> csiManager.handle(ch);
                    case SHIFT_IN_CHARACTER_SET, SHIFT_OUT_CHARACTER_SET -> {
                        state = State.NORMAL;
                        switch (ch) {
                            case 'A' -> {
                            } // United Kingdom Set
                            case 'B' -> drawingModeG0 = DrawingMode.ASCII; // ASCII Set
                            case '0' -> drawingModeG0 = DrawingMode.SPECIAL_GRAPHICS; // Special Graphics
                            case '1' -> {
                            } // Alternate Character ROM Standard Character Set
                            case '2' -> {
                            } // Alternate Character ROM Special Graphics
                        }
                    }
                    case HASH -> {
                        state = State.NORMAL;
                        switch (ch) {
                            case '3' -> {
                            } // Change this line to double-height top half (DECDHL)
                            case '4' -> {
                            } // Change this line to double-height bottom half (DECDHL)
                            case '5' -> {
                            } // Change this line to single-width single-height (DECSWL)
                            case '6' -> {
                            } // Change this line to double-width single-height (DECDWL)
                            case '8' -> { // Fill Screen with Es (DECALN)
                                if (currentPrivateModeState.isAltBufferEnabled()) {
                                    Arrays.fill(altBuffer, 'E');
                                } else {
                                    Arrays.fill(buffer, (lastRowToDisplayMax - HEIGHT) * WIDTH, ((WIDTH - 1) + (HEIGHT - 1) * WIDTH) + 1, 'E');
                                }
                                renderers.forEach(model -> model.getDirtyMask().set(-1));
                            }
                        }
                    }
                    case DCS -> dcsManager.handle(ch); // Used for mapping Function keys and possibly other things
                    case OSC -> oscManager.handle(ch);
                    case APC -> apcManager.handle(ch);
                }
            }
        }
    }

    public synchronized void putInput(final char value) {
        putInput((byte) value);
    }

    public synchronized void putInput(final byte value) {
        input.enqueue(value);
    }

    public void putResponse(final String value) {
        for (int i = 0; i < value.length(); i++) {
            putResponse((byte) value.charAt(i));
        }
    }

    public void putResponse(final byte value) {
        if (!displayOnly) {
            putInput(value);
        }
    }

    public void shiftUp(int count) {
        if (currentPrivateModeState.isAltBufferEnabled()) {
            shiftLines(scrollFirst + 1, scrollLast, -count);
        } else {
            if (lastRowToDisplay == HEIGHT * SCROLL_BACK_COUNT || scrollLast != Terminal.HEIGHT - 1 || scrollFirst != 0) {
                shiftLines(scrollFirst != 0 ? (scrollFirst + (lastRowToDisplayMax - HEIGHT)) + 1 : 1, scrollLast != Terminal.HEIGHT - 1 ? scrollLast + (lastRowToDisplayMax - HEIGHT) : (HEIGHT * SCROLL_BACK_COUNT) - 1, -count);
            }
        }
    }

    public void shiftDown(int count) {
        if (currentPrivateModeState.isAltBufferEnabled()) {
            shiftLines(scrollFirst, scrollLast - 1, count);
        } else {
            shiftLines(scrollFirst != 0 ? (scrollFirst + (lastRowToDisplayMax - HEIGHT)) : 0, scrollLast != Terminal.HEIGHT - 1 ? scrollLast + (lastRowToDisplayMax - HEIGHT) - 1 : ((HEIGHT * SCROLL_BACK_COUNT) - 1) - 1, count);
        }
    }

    public void shiftUpOne() {
        shiftUp(1);
    }

    public void shiftDownOne() {
        shiftDown(1);
    }

    public void shiftLines(final int firstLine, final int lastLine, final int count) {
        if (count == 0)
            return;

        final int srcIndex = firstLine * WIDTH;
        final int charCount = (lastLine + 1) * WIDTH - srcIndex;
        final int dstIndex = srcIndex + count * WIDTH;
        ColorData c;
        switch (currentBackgroundColorMode) {
            case SIXTEEN_COLOR -> c = sixteenColor;
            case TWO_FIFTY_SIX_COLOR -> c = twoFiftySixColor;
            case TRUE_COLOR -> c = backgroundColor;
            case SIXTEEN_COLOR_BRIGHT -> c = sixteenColorBright;
            default -> c = Terminal.DEFAULT_BACKGROUND_COLOR;
        }
        final int shiftUpOrDown = count > 0 ? srcIndex : (dstIndex + charCount);
        if (currentPrivateModeState.isAltBufferEnabled()) {
            System.arraycopy(altBuffer, srcIndex, altBuffer, dstIndex, charCount);
            System.arraycopy(altColors, srcIndex, altColors, dstIndex, charCount);
            System.arraycopy(altColorsBackground, srcIndex, altColorsBackground, dstIndex, charCount);
            System.arraycopy(altStyles, srcIndex, altStyles, dstIndex, charCount);

            // TODO Copy color and style from last line.
            // TODO Copy color and style from last line.
            final int clearCount = Math.abs(count * WIDTH);
            Arrays.fill(altBuffer, shiftUpOrDown, shiftUpOrDown + clearCount, ' ');
            Arrays.fill(altColors, shiftUpOrDown, shiftUpOrDown + clearCount, DEFAULT_COLORS.Copy());
            Arrays.fill(altColorsBackground, shiftUpOrDown, shiftUpOrDown + clearCount, c.Copy());
            Arrays.fill(altStyles, shiftUpOrDown, shiftUpOrDown + clearCount, DEFAULT_STYLE);

            int dirtyLinesMask = 0;
            final int dirtyStart = Math.min(firstLine, firstLine + count);
            final int dirtyEnd = Math.max(lastLine, lastLine + count);
            for (int i = dirtyStart; i <= dirtyEnd; i++) {
                dirtyLinesMask |= 1 << i;
            }
            final int finalDirtyLinesMask = dirtyLinesMask;
            renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(finalDirtyLinesMask, (left, right) -> left | right));
        } else {
            System.arraycopy(buffer, srcIndex, buffer, dstIndex, charCount);
            System.arraycopy(colors, srcIndex, colors, dstIndex, charCount);
            System.arraycopy(colorsBackground, srcIndex, colorsBackground, dstIndex, charCount);
            System.arraycopy(styles, srcIndex, styles, dstIndex, charCount);

            // TODO Copy color and style from last line.
            // TODO Copy color and style from last line.
            final int clearCount = Math.abs(count * WIDTH);
            Arrays.fill(buffer, shiftUpOrDown, shiftUpOrDown + clearCount, ' ');
            Arrays.fill(colors, shiftUpOrDown, shiftUpOrDown + clearCount, DEFAULT_COLORS.Copy());
            Arrays.fill(colorsBackground, shiftUpOrDown, shiftUpOrDown + clearCount, c.Copy());
            Arrays.fill(styles, shiftUpOrDown, shiftUpOrDown + clearCount, DEFAULT_STYLE);

            int dirtyLinesMask = 0;
            final int dirtyStart = Math.min(firstLine, firstLine + count);
            final int dirtyEnd = Math.max(lastLine, lastLine + count);
            for (int i = dirtyStart; i <= dirtyEnd; i++) {
                int globalI = lastRowToDisplayMax - (HEIGHT - i);
                int localI = HEIGHT + (globalI - lastRowToDisplay);
                dirtyLinesMask |= 1 << localI;
            }
            final int finalDirtyLinesMask = dirtyLinesMask;
            renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(finalDirtyLinesMask, (left, right) -> left | right));
        }
    }

    private void putChar(int ch) {
        if (continuationByte) continuationByte = false;
        if (Character.isISOControl(ch))
            return;

        int curMode = (useG0) ? drawingModeG0 : drawingModeG1;

        if (curMode == DrawingMode.SPECIAL_GRAPHICS) {
            switch (ch) {
                case 'l' -> ch = "┌".codePointAt(0);
                case 'k' -> ch = "┐".codePointAt(0);
                case 'm' -> ch = "└".codePointAt(0);
                case 'j' -> ch = "┘".codePointAt(0);
                case 'q' -> ch = "─".codePointAt(0);
                case 'x' -> ch = "│".codePointAt(0);
                case 'n' -> ch = "┼".codePointAt(0);
                case '~' -> ch = "B".codePointAt(0);
                case 'u' -> ch = "┤".codePointAt(0);
                case 't' -> ch = "├".codePointAt(0);
                case 'v' -> ch = "┴".codePointAt(0);
                case 'w' -> ch = "┬".codePointAt(0);
            }
        }

        if (x >= WIDTH) {
            if (currentPrivateModeState.DECAWM) {
                NEL.execute(this);
            } else {
                setCursorPos(WIDTH - 1, y);
            }
        }

        setChar(x, y, ch);
        x++;
    }

    private void setChar(final int x, final int y, final int ch) {
        if (currentPrivateModeState.isAltBufferEnabled()) {
            final int index = x + y * WIDTH;

            altBuffer[index] = ch;

            switch (currentForegroundColorMode) {
                case SIXTEEN_COLOR -> altColors[index] = sixteenColor.Copy();
                case TWO_FIFTY_SIX_COLOR -> altColors[index] = twoFiftySixColor.Copy();
                case TRUE_COLOR -> altColors[index] = foregroundColor.Copy();
                case SIXTEEN_COLOR_BRIGHT -> altColors[index] = sixteenColorBright.Copy();
            }

            switch (currentBackgroundColorMode) {
                case SIXTEEN_COLOR -> altColorsBackground[index] = sixteenColor.Copy();
                case TWO_FIFTY_SIX_COLOR -> altColorsBackground[index] = twoFiftySixColor.Copy();
                case TRUE_COLOR -> altColorsBackground[index] = backgroundColor.Copy();
                case SIXTEEN_COLOR_BRIGHT -> altColorsBackground[index] = sixteenColorBright.Copy();
            }

            altStyles[index] = style;
            renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(1 << (y), (prev, next) -> prev | next));
        } else {
            int correctedY = (y + (lastRowToDisplayMax - HEIGHT));
            final int index = x + correctedY * WIDTH;

            buffer[index] = ch;

            switch (currentForegroundColorMode) {
                case SIXTEEN_COLOR -> colors[index] = sixteenColor.Copy();
                case TWO_FIFTY_SIX_COLOR -> colors[index] = twoFiftySixColor.Copy();
                case TRUE_COLOR -> colors[index] = foregroundColor.Copy();
                case SIXTEEN_COLOR_BRIGHT -> colors[index] = sixteenColorBright.Copy();
            }

            switch (currentBackgroundColorMode) {
                case SIXTEEN_COLOR -> colorsBackground[index] = sixteenColor.Copy();
                case TWO_FIFTY_SIX_COLOR -> colorsBackground[index] = twoFiftySixColor.Copy();
                case TRUE_COLOR -> colorsBackground[index] = backgroundColor.Copy();
                case SIXTEEN_COLOR_BRIGHT -> colorsBackground[index] = sixteenColorBright.Copy();
            }

            styles[index] = style;
            int globalY = lastRowToDisplayMax - (HEIGHT - y);
            int localY = HEIGHT + (globalY - lastRowToDisplay);
            renderers.forEach(model -> model.getDirtyMask().accumulateAndGet(1 << (localY), (prev, next) -> prev | next));
        }
    }

    // Renderer
    @OnlyIn(Dist.CLIENT)
    public static final class Renderer implements RendererModel, RendererView {
        public static final int[] BRIGHT_COLORS = {
            0x7B7B7B, // Black
            0xFF4524, // Red
            0x3AFF5B, // Green
            0xFFDB10, // Yellow
            0x1281FF, // Blue
            0xFF3ADE, // Magenta
            0x27DDFF, // Cyan
            0xFFFFFF, // White
            0xEEEEEE, // Default Foreground
            0x000000, // Default Background
        };

        public static final int[] COLORS = {
            0x555555, // Black
            0xEE3322, // Red
            0x33DD44, // Green
            0xFFCC11, // Yellow
            0x1188EE, // Blue
            0xDD33CC, // Magenta
            0x22CCDD, // Cyan
            0xEEEEEE, // White
            0xEEEEEE, // Default Foreground
            0x000000, // Default Background
        };

        public static final int[] DIM_COLORS = {
            0x000000, // Black
            0x772211, // Red
            0x116622, // Green
            0x886611, // Yellow
            0x115588, // Blue
            0x771177, // Magenta
            0x116677, // Cyan
            0x777777, // White
            0xEEEEEE, // Default Foreground
            0x000000, // Default Background
        };

        public static final int[] COLORS_256 = {
            0x000000, 0x772211, 0x116622, 0x886611, 0x115588, 0x771177, 0x116677, 0x777777,
            0x555555, 0xEE3322, 0x33DD44, 0xFFCC11, 0x1188EE, 0xDD33CC, 0x22CCDD, 0xEEEEEE,
            0x000000, 0x00005f, 0x000087, 0x0000af, 0x0000d7, 0x0000ff, 0x005f00, 0x005f5f,
            0x005f87, 0x005faf, 0x005fd7, 0x005fff, 0x008700, 0x00875f, 0x008787, 0x0087af,
            0x0087d7, 0x0087ff, 0x00af00, 0x00af5f, 0x00af87, 0x00afaf, 0x00afd7, 0x00afff,
            0x00d700, 0x00d75f, 0x00d787, 0x00d7af, 0x00d7d7, 0x00d7ff, 0x00ff00, 0x00ff5f,
            0x00ff87, 0x00ffaf, 0x00ffd7, 0x00ffff, 0x5f0000, 0x5f005f, 0x5f0087, 0x5f00af,
            0x5f00d7, 0x5f00ff, 0x5f5f00, 0x5f5f5f, 0x5f5f87, 0x5f5faf, 0x5f5fd7, 0x5f5fff,
            0x5f8700, 0x5f875f, 0x5f8787, 0x5f87af, 0x5f87d7, 0x5f87ff, 0x5faf00, 0x5faf5f,
            0x5faf87, 0x5fafaf, 0x5fafd7, 0x5fafff, 0x5fd700, 0x5fd75f, 0x5fd787, 0x5fd7af,
            0x5fd7d7, 0x5fd7ff, 0x5fff00, 0x5fff5f, 0x5fff87, 0x5fffaf, 0x5fffd7, 0x5fffff,
            0x870000, 0x87005f, 0x870087, 0x8700af, 0x8700d7, 0x8700ff, 0x875f00, 0x875f5f,
            0x875f87, 0x875faf, 0x875fd7, 0x875fff, 0x878700, 0x87875f, 0x878787, 0x8787af,
            0x8787d7, 0x8787ff, 0x87af00, 0x87af5f, 0x87af87, 0x87afaf, 0x87afd7, 0x87afff,
            0x87d700, 0x87d75f, 0x87d787, 0x87d7af, 0x87d7d7, 0x87d7ff, 0x87ff00, 0x87ff5f,
            0x87ff87, 0x87ffaf, 0x87ffd7, 0x87ffff, 0xaf0000, 0xaf005f, 0xaf0087, 0xaf00af,
            0xaf00d7, 0xaf00ff, 0xaf5f00, 0xaf5f5f, 0xaf5f87, 0xaf5faf, 0xaf5fd7, 0xaf5fff,
            0xaf8700, 0xaf875f, 0xaf8787, 0xaf87af, 0xaf87d7, 0xaf87ff, 0xafaf00, 0xafaf5f,
            0xafaf87, 0xafafaf, 0xafafd7, 0xafafff, 0xafd700, 0xafd75f, 0xafd787, 0xafd7af,
            0xafd7d7, 0xafd7ff, 0xafff00, 0xafff5f, 0xafff87, 0xafffaf, 0xafffd7, 0xafffff,
            0xd70000, 0xd7005f, 0xd70087, 0xd700af, 0xd700d7, 0xd700ff, 0xd75f00, 0xd75f5f,
            0xd75f87, 0xd75faf, 0xd75fd7, 0xd75fff, 0xd78700, 0xd7875f, 0xd78787, 0xd787af,
            0xd787d7, 0xd787ff, 0xdfaf00, 0xdfaf5f, 0xdfaf87, 0xdfafaf, 0xdfafdf, 0xdfafff,
            0xdfdf00, 0xdfdf5f, 0xdfdf87, 0xdfdfaf, 0xdfdfdf, 0xdfdfff, 0xdfff00, 0xdfff5f,
            0xdfff87, 0xdfffaf, 0xdfffdf, 0xdfffff, 0xff0000, 0xff005f, 0xff0087, 0xff00af,
            0xff00df, 0xff00ff, 0xff5f00, 0xff5f5f, 0xff5f87, 0xff5faf, 0xff5fdf, 0xff5fff,
            0xff8700, 0xff875f, 0xff8787, 0xff87af, 0xff87df, 0xff87ff, 0xffaf00, 0xffaf5f,
            0xffaf87, 0xffafaf, 0xffafdf, 0xffafff, 0xffdf00, 0xffdf5f, 0xffdf87, 0xffdfaf,
            0xffdfdf, 0xffdfff, 0xffff00, 0xffff5f, 0xffff87, 0xffffaf, 0xffffdf, 0xffffff,
            0x080808, 0x121212, 0x1c1c1c, 0x262626, 0x303030, 0x3a3a3a, 0x444444, 0x4e4e4e,
            0x585858, 0x626262, 0x6c6c6c, 0x767676, 0x808080, 0x8a8a8a, 0x949494, 0x9e9e9e,
            0xa8a8a8, 0xb2b2b2, 0xbcbcbc, 0xc6c6c6, 0xd0d0d0, 0xdadada, 0xe4e4e4, 0xeeeeee
        };

        /// ////////////////////////////////////////////////////////////

        public final Terminal terminal;
        public final VertexBuffer[] lines = new VertexBuffer[HEIGHT];

        public final AtomicInteger dirty = new AtomicInteger(-1);

        /// ////////////////////////////////////////////////////////////

        public Renderer(final Terminal terminal) {
            this.terminal = terminal;
        }

        /// ////////////////////////////////////////////////////////////

        @Override
        public void render(final PoseStack stack, final Matrix4f projectionMatrix) {
            if (terminal.currentPrivateModeState.APPLICATION_SYNC) return;
            validateLineCache();
            renderBuffer(stack, projectionMatrix);

            boolean steady = switch (terminal.cursorMode) {
                case CursorMode.STEADY_BLOCK, CursorMode.STEADY_UNDERLINE, CursorMode.STEADY_BAR_LINE -> true;
                default -> false;
            };

            if (steady || (System.currentTimeMillis() + terminal.hashCode()) % 1000 > 500) {
                renderCursor(stack);
            }
        }

        @Override
        public AtomicInteger getDirtyMask() {
            return dirty;
        }

        @Override
        public void close() {
            for (int i = 0; i < lines.length; i++) {
                final VertexBuffer line = lines[i];
                if (line != null) {
                    line.close();
                    lines[i] = null;
                }
            }
        }

        /// ////////////////////////////////////////////////////////////

        public int findLineIndex(VertexBuffer[] vba, VertexBuffer vb) {
            int i = 0;
            while (i < vba.length) {
                if (vba[i] == vb) {
                    return i;
                }
                i++;
            }
            return -1;
        }

        public void renderBuffer(final PoseStack stack, final Matrix4f projectionMatrix) {
            final ShaderInstance shader = GameRenderer.getPositionColorTexShader();
            if (shader == null) {
                return;
            }

            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, FontHandling.getAtlas());

            for (final VertexBuffer line : lines) {
                if (!line.isInvalid()) {
                    try {
                        line.bind();
                        line.drawWithShader(stack.last().pose(), projectionMatrix, shader);
                        VertexBuffer.unbind();
                    } catch (Exception e) {
                        System.out.println("ERROR: " + e.getMessage());
                        System.out.println(findLineIndex(lines, line));
                    }
                }
            }

            RenderSystem.depthMask(true);
        }

        @SuppressWarnings("resource")
        public void validateLineCache() {
            if (dirty.get() == 0) {
                return;
            }

            final int mask = dirty.getAndSet(0);
            for (int row = 0; row < lines.length; row++) {
                if ((mask & (1 << row)) == 0) {
                    continue;
                }
                BufferBuilder builder = Tesselator.getInstance().getBuilder();

                final Matrix4f matrix = new Matrix4f().translate(0, row * CHAR_HEIGHT, 0);

                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

                renderBackground(matrix, builder, row);
                renderForeground(matrix, builder, row);

                BufferBuilder.RenderedBuffer rb = builder.end();

                if (lines[row] == null) {
                    lines[row] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                } else if (lines[row] != null) {
                    lines[row].close();
                    lines[row] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                }

                if (!lines[row].isInvalid()) {
                    lines[row].bind();
                    lines[row].upload(rb);
                    VertexBuffer.unbind();
                }
            }
        }

        public void renderBackground(final Matrix4f matrix, final BufferBuilder buffer, final int row) {
            // State tracking for drawing background quads spanning multiple characters.
            float backgroundStartX = -1;
            int backgroundColor = 0;

            float tx = 0f;
            boolean useAltBuffer = terminal.currentPrivateModeState.isAltBufferEnabled();
            for (int col = 0, index = useAltBuffer ? row * WIDTH : (row + (terminal.lastRowToDisplay - HEIGHT)) * WIDTH; col < WIDTH; col++, index++) {
                final byte style = useAltBuffer ? terminal.altStyles[index] : terminal.styles[index];
                final boolean invertBackground = (style & STYLE_INVERT_MASK) != 0;
                final ColorData color = !invertBackground ? useAltBuffer ? terminal.altColorsBackground[index] : terminal.colorsBackground[index] : useAltBuffer ? terminal.altColors[index] : terminal.colors[index];

                if ((style & STYLE_HIDDEN_MASK) != 0) continue;

                final int[] palette = (style & STYLE_DIM_MASK) != 0 ? DIM_COLORS : COLORS;
                int background = switch (color.Mode) {
                    case SIXTEEN_COLOR -> palette[!invertBackground ? color.G : color.R];
                    case TWO_FIFTY_SIX_COLOR -> COLORS_256[!invertBackground ? color.G : color.R];
                    case TRUE_COLOR -> color.ToInt();
                    case SIXTEEN_COLOR_BRIGHT -> BRIGHT_COLORS[!invertBackground ? color.G : color.R];
                    case DEFAULT_BACKGROUND -> 0x000000;
                };

                final boolean hadBackground = backgroundStartX >= 0;
                final boolean hasBackground = background != 0x000000;
                if (!hadBackground && hasBackground) {
                    backgroundStartX = tx;
                    backgroundColor = background;
                } else if (hadBackground && (!hasBackground || backgroundColor != background)) {
                    renderBackground(matrix, buffer, backgroundStartX, tx, backgroundColor);

                    if (hasBackground) {
                        backgroundStartX = tx;
                        backgroundColor = background;
                    } else {
                        backgroundStartX = -1;
                    }
                }

                tx += CHAR_WIDTH;
            }

            if (backgroundStartX >= 0) {
                renderBackground(matrix, buffer, backgroundStartX, tx, backgroundColor);
            }
        }

        public void renderBackground(final Matrix4f matrix, final BufferBuilder buffer, final float x0, final float x1, final int color) {
            final float r = ((color >> 16) & 0xFF) / 255f;
            final float g = ((color >> 8) & 0xFF) / 255f;
            final float b = (color & 0xFF) / 255f;

            buffer.vertex(matrix, x0, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(0, 0).endVertex();
            buffer.vertex(matrix, x1, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(0, 0).endVertex();
            buffer.vertex(matrix, x1, 0, 0).color(r, g, b, 1).uv(0, 0).endVertex();
            buffer.vertex(matrix, x0, 0, 0).color(r, g, b, 1).uv(0, 0).endVertex();
        }

        public void renderForeground(final Matrix4f matrix, final BufferBuilder buffer, final int row) {
            float tx = 0f;
            boolean useAltBuffer = terminal.currentPrivateModeState.isAltBufferEnabled();
            for (int col = 0, index = useAltBuffer ? row * WIDTH : (row + (terminal.lastRowToDisplay - HEIGHT)) * WIDTH; col < WIDTH; col++, index++) {
                final byte style = useAltBuffer ? terminal.altStyles[index] : terminal.styles[index];
                final boolean invertBackground = (style & STYLE_INVERT_MASK) != 0;
                final ColorData color = !invertBackground ? useAltBuffer ? terminal.altColors[index] : terminal.colors[index] : useAltBuffer ? terminal.altColorsBackground[index] : terminal.colorsBackground[index];

                if ((style & STYLE_HIDDEN_MASK) != 0) continue;

                final int[] palette = (style & STYLE_DIM_MASK) != 0 ? DIM_COLORS : COLORS;
                int foreground = switch (color.Mode) {
                    case SIXTEEN_COLOR -> palette[!invertBackground ? color.R : color.G];
                    case TWO_FIFTY_SIX_COLOR -> COLORS_256[!invertBackground ? color.R : color.G];
                    case TRUE_COLOR -> color.ToInt();
                    case SIXTEEN_COLOR_BRIGHT -> BRIGHT_COLORS[!invertBackground ? color.R : color.G];
                    case DEFAULT_BACKGROUND -> 0x000000;
                };

                final int character = (useAltBuffer) ? terminal.altBuffer[index] : terminal.buffer[index];

                renderForeground(matrix, buffer, tx, character, foreground, style);

                tx += CHAR_WIDTH;
            }
        }

        public void renderForeground(final Matrix4f matrix, final BufferBuilder buffer, final float offset, final int character, final int color, final byte style) {
            final float r = ((color >> 16) & 0xFF) / 255f;
            final float g = ((color >> 8) & 0xFF) / 255f;
            final float b = (color & 0xFF) / 255f;

            if (isPrintableCharacter(character)) {
                FontHandling.FontStyle font = getFontStyle(style);

                Glyph glyph = FontHandling.getGlyph(character, font);

                if (font == FontHandling.FontStyle.ITALIC || font == FontHandling.FontStyle.BOLD_ITALIC) { // Italic
                    buffer.vertex(matrix, offset, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(glyph.uStart, glyph.vEnd).endVertex();
                    buffer.vertex(matrix, offset + CHAR_WIDTH + 8, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(glyph.uEnd, glyph.vEnd).endVertex();
                    buffer.vertex(matrix, offset + CHAR_WIDTH + 8, 0, 0).color(r, g, b, 1).uv(glyph.uEnd, glyph.vStart).endVertex();
                    buffer.vertex(matrix, offset, 0, 0).color(r, g, b, 1).uv(glyph.uStart, glyph.vStart).endVertex();
                }
                else
                {
                    buffer.vertex(matrix, offset, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(glyph.uStart, glyph.vEnd).endVertex();
                    buffer.vertex(matrix, offset + CHAR_WIDTH, CHAR_HEIGHT, 0).color(r, g, b, 1).uv(glyph.uEnd, glyph.vEnd).endVertex();
                    buffer.vertex(matrix, offset + CHAR_WIDTH, 0, 0).color(r, g, b, 1).uv(glyph.uEnd, glyph.vStart).endVertex();
                    buffer.vertex(matrix, offset, 0, 0).color(r, g, b, 1).uv(glyph.uStart, glyph.vStart).endVertex();
                }
            }

            if ((style & STYLE_UNDERLINE_MASK) != 0) {
                buffer.vertex(matrix, offset, CHAR_HEIGHT - 3, 0).color(r, g, b, 1).uv(0, 0).endVertex();
                buffer.vertex(matrix, offset + CHAR_WIDTH, CHAR_HEIGHT - 3, 0).color(r, g, b, 1).uv(0, 0).endVertex();
                buffer.vertex(matrix, offset + CHAR_WIDTH, CHAR_HEIGHT - 2, 0).color(r, g, b, 1).uv(0, 0).endVertex();
                buffer.vertex(matrix, offset, CHAR_HEIGHT - 2, 0).color(r, g, b, 1).uv(0, 0).endVertex();
            }
        }

        private FontHandling.FontStyle getFontStyle(byte style) {
            FontHandling.FontStyle font;
            if ((style & STYLE_BOLD_MASK) != 0 && (style & STYLE_ITALIC_MASK) != 0) {
                font = FontHandling.FontStyle.BOLD_ITALIC;
            } else if ((style & STYLE_BOLD_MASK) != 0) {
                font = FontHandling.FontStyle.BOLD;
            } else if ((style & STYLE_ITALIC_MASK) != 0) {
                font = FontHandling.FontStyle.ITALIC;
            } else {
                font = FontHandling.FontStyle.REGULAR;
            }
            return font;
        }

        public void renderCursor(final PoseStack stack) {
            BufferUploader.reset();
            if (!terminal.currentPrivateModeState.DECTCEM) return;
            int globalY = terminal.lastRowToDisplayMax - (HEIGHT - terminal.y);
            int localY = HEIGHT + (globalY - terminal.lastRowToDisplay);

            boolean useAltBuffer = terminal.currentPrivateModeState.isAltBufferEnabled();

            if (terminal.x < 0 || terminal.x >= WIDTH || ((!useAltBuffer && localY < 0) || terminal.y < 0) || ((!useAltBuffer && localY >= HEIGHT) || terminal.y >= HEIGHT) || (!useAltBuffer && globalY > terminal.lastRowToDisplay)) {
                return;
            }

            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            stack.pushPose();
            stack.translate(terminal.x * CHAR_WIDTH, (useAltBuffer ? terminal.y : localY) * CHAR_HEIGHT, 0);

            final Matrix4f matrix = stack.last().pose();
            final BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            final int foreground = COLORS[Color.WHITE];
            final float r = ((foreground >> 16) & 0xFF) / 255f;
            final float g = ((foreground >> 8) & 0xFF) / 255f;
            final float b = ((foreground) & 0xFF) / 255f;

            switch (terminal.cursorMode) {
                case CursorMode.DEFAULT, CursorMode.BLINK_BLOCK, CursorMode.STEADY_BLOCK: // BLOCK
                    buffer.vertex(matrix, 0, CHAR_HEIGHT, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, CHAR_WIDTH, CHAR_HEIGHT, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, CHAR_WIDTH, 0, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, 0, 0, 0).color(r, g, b, 1).endVertex();
                    break;
                case CursorMode.BLINK_UNDERLINE, CursorMode.STEADY_UNDERLINE: // UNDERLINE
                    buffer.vertex(matrix, 0, 1, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, CHAR_WIDTH, 1, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, CHAR_WIDTH, 0, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, 0, 0, 0).color(r, g, b, 1).endVertex();
                    break;
                case CursorMode.BLINKING_BAR_LINE, CursorMode.STEADY_BAR_LINE: // VERTICAL BAR LINE
                    buffer.vertex(matrix, 0, CHAR_HEIGHT, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, 1, CHAR_HEIGHT, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, 1, 0, 0).color(r, g, b, 1).endVertex();
                    buffer.vertex(matrix, 0, 0, 0).color(r, g, b, 1).endVertex();
                    break;
            }

            BufferBuilder.RenderedBuffer rb = buffer.end();
            BufferUploader.drawWithShader(rb);

            stack.popPose();

            RenderSystem.depthMask(true);
        }

        public static boolean isPrintableCharacter(final int ch) {
            return ch == 0 ||
                (ch > ' ' && ch <= '~') ||
                ch >= 177;
        }
    }
}
