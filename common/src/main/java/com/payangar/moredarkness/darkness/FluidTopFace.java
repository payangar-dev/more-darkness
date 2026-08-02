package com.payangar.moredarkness.darkness;

/**
 * FIXME: fake - perception spike (fluid top face scratch).
 * The fluid top face currently in flight on this meshing thread: the corner
 * light values computed when the face starts, and the vertices accumulated as
 * vanilla emits them, so the turbidity layers can be stacked once the fourth
 * corner lands. On 1.21.11 the top face is four separate vertex(...) calls
 * (there is no single addFace call to wrap like on 26.1), hence the
 * accumulation; chunk meshing runs on several workers, hence the thread local.
 */
public final class FluidTopFace {

    public static final ThreadLocal<FluidTopFace> CURRENT = ThreadLocal.withInitial(FluidTopFace::new);

    private static final int VERTICES = 4;

    /** Smooth corner light, indexed by dz * 2 + dx (dx/dz 0 or 1 in the block). */
    private final int[] cornerLight = new int[4];

    public final float[] x = new float[VERTICES];
    public final float[] y = new float[VERTICES];
    public final float[] z = new float[VERTICES];
    public final float[] u = new float[VERTICES];
    public final float[] v = new float[VERTICES];
    public final int[] light = new int[VERTICES];
    public float red;
    public float green;
    public float blue;
    private int count;

    private FluidTopFace() {}

    /** Starts a new face with its four smoothed corner light values. */
    public void begin(int lightNW, int lightNE, int lightSW, int lightSE) {
        this.cornerLight[0] = lightNW;
        this.cornerLight[1] = lightNE;
        this.cornerLight[2] = lightSW;
        this.cornerLight[3] = lightSE;
        this.count = 0;
    }

    public int cornerLight(int dx, int dz) {
        return this.cornerLight[dz * 2 + dx];
    }

    /** True once the four vertices of the primary face have been seen. */
    public boolean isComplete() {
        return this.count == VERTICES;
    }

    public void add(float x, float y, float z, float u, float v, int light, float red, float green, float blue) {
        this.x[this.count] = x;
        this.y[this.count] = y;
        this.z[this.count] = z;
        this.u[this.count] = u;
        this.v[this.count] = v;
        this.light[this.count] = light;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.count++;
    }
}
