#version 150

// FIXME: fake - perception spike port (glare prefilter).
// Spencer 1995: glare comes from the energy the display cannot show. Boost
// the LDR frame by the adaptation mismatch, keep only what clips above 1.0.
// Stored pre-divided by K to fit the overflow into an RGBA8 target.

uniform sampler2D DiffuseSampler;

uniform vec4 Glare; // x = transient stops, y = bloom intensity, z = time, w = retinal gain stops

in vec2 texCoord;

out vec4 fragColor;

// Wider headroom than 26.x: this branch's CPU lightmap renders hotter
// highlights and K = 4 saturated the 8-bit overflow buffer, flattening
// the bloom and disconnecting it from the intensity knob.
const float K = 8.0;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    // The dark-adapted eye amplifies everything it sees: bright spots
    // overflow into a halo even while the frame average stays dark.
    vec3 boosted = color * exp2(Glare.w);
    vec3 overflow = max(boosted - 1.0, vec3(0.0));
    fragColor = vec4(clamp(overflow / K, 0.0, 1.0), 1.0);
}
