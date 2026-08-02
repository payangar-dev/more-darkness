#version 330

// FIXME: fake - perception spike step 3 (glare prefilter).
// Spencer 1995: glare comes from the energy the display cannot show. Boost
// the LDR frame by the adaptation mismatch, keep only what clips above 1.0.
// Stored pre-divided by K to fit the overflow into an RGBA8 target.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GlareConfig {
    vec4 Glare; // x = transient stops, y = bloom intensity, z = time, w = retinal gain stops
};

out vec4 fragColor;

const float K = 4.0;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    // The dark-adapted eye amplifies everything it sees: bright spots
    // overflow into a halo even while the frame average stays dark.
    vec3 boosted = color * exp2(Glare.w);
    vec3 overflow = max(boosted - 1.0, vec3(0.0));
    fragColor = vec4(clamp(overflow / K, 0.0, 1.0), 1.0);
}
