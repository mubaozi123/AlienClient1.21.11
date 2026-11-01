#version 150

uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

uniform RiyueParams {
    vec4 Color;
    vec4 BackgroundColor;
    vec4 Resolution; // x=width y=height z=time w=mixFactor(opacity)
    vec4 Params;     // x=scale y=speed z=applyAll w=unused
    vec4 Unused1;
    vec4 Unused2;
    vec4 Unused3;
};

#define TAU 6.28318530718
#define MAX_ITER 5
#define INTENSITY 0.005

void main() {
    vec4 scene = texture(InSampler, texCoord);
    float opacity = clamp(Resolution.w, 0.0, 1.0);
    if (opacity <= 0.0) {
        fragColor = scene;
        return;
    }

    vec2 resolution = max(Resolution.xy, vec2(1.0));
    vec2 uv = texCoord - vec2(0.5);
    uv.x *= resolution.x / resolution.y;
    uv = uv * Params.x + vec2(0.5);
    vec2 p = mod(uv * TAU, TAU) - 250.0;

    vec2 iterPos = p;
    float value = 1.0;
    float timeOffset = Resolution.z * 0.5 + 23.0;
    float invMaxIter = 1.0 / float(MAX_ITER);

    for (int n = 0; n < MAX_ITER; n++) {
        float iterTime = timeOffset * (1.0 - (3.5 / float(n + 1)));
        iterPos = p + vec2(
            cos(iterTime - iterPos.x) + sin(iterTime + iterPos.y),
            sin(iterTime - iterPos.y) + cos(iterTime + iterPos.x)
        );

        float sinX = sin(iterPos.x + iterTime);
        float cosY = cos(iterPos.y + iterTime);
        float compX = p.x * INTENSITY / max(abs(sinX), 0.001);
        float compY = p.y * INTENSITY / max(abs(cosY), 0.001);
        value += inversesqrt(compX * compX + compY * compY);
    }

    value = 1.17 - pow(value * invMaxIter, 1.4);

    float pwr = value * value;
    pwr = pwr * pwr;
    pwr = pwr * pwr;

    vec3 baseColor = vec3(abs(pwr));
    vec3 enhancedColor = clamp(baseColor + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    float luminance = dot(enhancedColor, vec3(0.299, 0.587, 0.114));

    float alpha = clamp(luminance * opacity, 0.0, 1.0);
    vec3 background = mix(vec3(0.01, 0.02, 0.05), BackgroundColor.rgb, clamp(BackgroundColor.a, 0.0, 1.0));
    vec3 overlay = vec3(luminance) * clamp(Color.rgb, 0.0, 1.0) * 1.4;
    vec3 sky = mix(background, overlay, alpha);

    if (Params.z > 0.5) {
        // ApplyAll: 纯天空覆盖全屏
        fragColor = vec4(sky, 1.0);
    } else {
        // 默认: 天空仅替换暗部(原天空区域)，保留方块/实体等前景
        float sceneLum = dot(scene.rgb, vec3(0.299, 0.587, 0.114));
        float mask = 1.0 - smoothstep(0.02, 0.18, sceneLum);
        fragColor = vec4(mix(scene.rgb, sky, mask), 1.0);
    }
}
