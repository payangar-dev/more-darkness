#version 330

// FIXME: fake - perception spike step 3d (dark sight radius).
// Dark adaptation only reveals the player's immediate surroundings: beyond
// the radius, pixels that only the adaptation floor lit up are crushed back
// to black, while real lights (torches, moonlit ground) stay visible.

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DarkSightConfig {
    vec4 DarkSight; // x = radius blocks, y = adaptation floor, z = near plane, w = far plane
};

out vec4 fragColor;

void main() {
    vec4 color = texture(MainSampler, texCoord);
    float depth = texture(MainDepthSampler, texCoord).r;
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // 26.2 uses reversed-Z: the projection swaps near/far, so the buffer
    // holds 1 at the near plane and 0 at the far plane. This linearization
    // is the same for both clip conventions (GL -1..1 and 0..1).
    float near = DarkSight.z;
    float far = DarkSight.w;
    float viewZ = near * far / (near + depth * (far - near));

    // The sky writes no depth and the buffer clears to 0: leave it alone
    float isSky = 1.0 - step(0.000001, depth);
    float beyond = smoothstep(DarkSight.x - 3.0, DarkSight.x, viewZ) * (1.0 - isSky);

    // Crush only what the adaptation floor could have lit (luma near the
    // floor level), keep anything genuinely brighter
    float keep = smoothstep(DarkSight.y * 0.6, DarkSight.y * 1.8, luma);

    fragColor = vec4(color.rgb * mix(1.0, keep, beyond), 1.0);
}
