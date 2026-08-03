#version 150

// Dark sight radius.
// Dark adaptation only reveals the player's immediate surroundings: beyond
// the radius, pixels that only the adaptation floor lit up are crushed back
// to black, while real lights (torches, moonlit ground) stay visible.

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;

uniform vec4 DarkSight; // x = radius blocks, y = adaptation floor, z = near plane, w = far plane

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float depth = texture(MainDepthSampler, texCoord).r;
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    float zNdc = depth * 2.0 - 1.0;
    float near = DarkSight.z;
    float far = DarkSight.w;
    float viewZ = 2.0 * near * far / (far + near - zNdc * (far - near));

    // The sky writes no depth: leave it alone
    float isSky = step(0.999999, depth);
    float beyond = smoothstep(DarkSight.x - 3.0, DarkSight.x, viewZ) * (1.0 - isSky);

    // Crush only what the adaptation floor could have lit (luma near the
    // floor level), keep anything genuinely brighter. The floor is clamped
    // away from zero so the smoothstep edges stay ordered.
    float floorLuma = max(DarkSight.y, 1.0e-4);
    float keep = smoothstep(floorLuma * 0.6, floorLuma * 1.8, luma);

    fragColor = vec4(color.rgb * mix(1.0, keep, beyond), 1.0);
}
