#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 uv = texCoord;
    float line = sin((uv.y * InSize.y + Time * 900.0) * 0.13) * 0.0018;
    float tearBand = smoothstep(0.022, 0.0, abs(fract(uv.y * 2.0 + Time * 0.9) - 0.5));
    float tear = (hash(vec2(floor(uv.y * 160.0), floor(Time * 50.0))) - 0.5) * 0.012 * tearBand;
    uv.x = fract(uv.x + line + tear);

    float chroma = 0.0018 + tearBand * 0.0025;
    float r = texture(DiffuseSampler, vec2(fract(uv.x + chroma), uv.y)).r;
    float g = texture(DiffuseSampler, uv).g;
    float b = texture(DiffuseSampler, vec2(fract(uv.x - chroma), uv.y)).b;

    float scan = 0.94 + 0.06 * sin(uv.y * InSize.y * 3.14159265);
    float grain = (hash(vec2(uv * InSize + Time * 130.0)) - 0.5) * 0.055;
    vec3 color = vec3(r, g, b) * scan + grain;
    color *= 0.985 - 0.12 * pow(abs(uv.y - 0.5) * 2.0, 2.0);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
