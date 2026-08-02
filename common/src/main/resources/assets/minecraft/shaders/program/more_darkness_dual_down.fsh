#version 150

// Dual filtering downsample, 5 taps (Bjorge, ARM, SIGGRAPH 2015).

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 halfpixel = 0.5 / OutSize;
    vec4 sum = texture(DiffuseSampler, texCoord) * 4.0;
    sum += texture(DiffuseSampler, texCoord - halfpixel);
    sum += texture(DiffuseSampler, texCoord + halfpixel);
    sum += texture(DiffuseSampler, texCoord + vec2(halfpixel.x, -halfpixel.y));
    sum += texture(DiffuseSampler, texCoord - vec2(halfpixel.x, -halfpixel.y));
    fragColor = sum / 8.0;
}
