#version 330

// Dual filtering downsample, 5 taps (Bjorge, ARM, SIGGRAPH 2015).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

void main() {
    vec2 halfpixel = 0.5 / OutSize;
    vec4 sum = texture(InSampler, texCoord) * 4.0;
    sum += texture(InSampler, texCoord - halfpixel);
    sum += texture(InSampler, texCoord + halfpixel);
    sum += texture(InSampler, texCoord + vec2(halfpixel.x, -halfpixel.y));
    sum += texture(InSampler, texCoord - vec2(halfpixel.x, -halfpixel.y));
    fragColor = sum / 8.0;
}
