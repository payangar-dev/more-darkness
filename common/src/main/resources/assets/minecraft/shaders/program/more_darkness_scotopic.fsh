#version 150

// FIXME: fake - perception spike port (scotopic vision), tuned curve.
// Mesopic model: color fades gradually and only for truly dim pixels
// (cones keep working surprisingly low), and the gray rod vision only
// exists once the eye is dark-adapted (rod-cone break): an unadapted eye
// in the dark sees black, not gray. Slight blue cast (Purkinje effect).

uniform sampler2D DiffuseSampler;

uniform vec4 Scotopic; // x = rod engagement 0..1 (from the adaptation state)

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Per-pixel mesopic ramp: full color above ~0.18 luma, colorless below ~0.02
    float dim = 1.0 - smoothstep(0.02, 0.18, luma);

    // Rods must be engaged for gray vision to exist at all
    float scotopic = dim * Scotopic.x;

    // Monochrome with a subtle cool cast
    vec3 nightGray = luma * vec3(0.85, 0.95, 1.12);

    vec3 outColor = mix(color.rgb, nightGray, scotopic * 0.92);
    fragColor = vec4(outColor, 1.0);
}
