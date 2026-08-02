#version 330

// Dual filtering upsample, 8 taps (Bjorge, ARM, SIGGRAPH 2015).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

void main() {
    vec2 halfpixel = 0.5 / InSize;
    vec4 sum = texture(InSampler, texCoord + vec2(-halfpixel.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(-halfpixel.x, halfpixel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, halfpixel.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(halfpixel.x, halfpixel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(halfpixel.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(halfpixel.x, -halfpixel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -halfpixel.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(-halfpixel.x, -halfpixel.y)) * 2.0;
    fragColor = sum / 12.0;
}
