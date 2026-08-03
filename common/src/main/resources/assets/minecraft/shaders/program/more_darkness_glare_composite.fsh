#version 150

// Glare composite.
// Rebuilds the frame with the overexposure boost applied, adds the blurred
// veil back on top, and dithers to hide 8-bit banding in the halo
// (Vlachos, Valve, GDC 2015).

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;

uniform vec4 Glare; // x = transient stops, y = bloom intensity, z = time, w = retinal gain stops

in vec2 texCoord;

out vec4 fragColor;

// Must match the prefilter headroom
const float K = 8.0;

vec3 screenSpaceDither(vec2 screenPos, float time) {
    vec3 dither = vec3(dot(vec2(171.0, 231.0), screenPos + time));
    dither = fract(dither / vec3(103.0, 71.0, 97.0)) - vec3(0.5);
    return (dither / 255.0) * 0.375;
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;

    // Transient overexposure with a filmic soft clip: a luminous veil that
    // keeps some detail instead of a flat white wall. Blended in smoothly so
    // zero mismatch is a strict identity.
    float fade = smoothstep(0.0, 0.4, Glare.x);
    vec3 exposed = 1.0 - exp(-color * exp2(Glare.x));
    vec3 boosted = mix(color, exposed, fade);
    // The halo stands on its own: its source already scales with the gain
    vec3 outColor = boosted + bloom * K * Glare.y;
    outColor += screenSpaceDither(gl_FragCoord.xy, Glare.z);

    fragColor = vec4(clamp(outColor, 0.0, 1.0), 1.0);
}
