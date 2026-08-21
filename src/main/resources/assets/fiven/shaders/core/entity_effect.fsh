#version 150
in vec4 vertexColor;
uniform vec4 ColorModulator;
uniform float FivenTime;
uniform float EffectKind;
out vec4 fragColor;
float h(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}
void main(){float n=h(floor(gl_FragCoord.xy*0.35)+floor(FivenTime*14.0));if(EffectKind<0.5){float flick=0.72+0.28*n;fragColor=vec4(vertexColor.rgb*flick,vertexColor.a*(0.72+0.28*n))*ColorModulator;}else{float pulse=0.78+0.22*sin(FivenTime*8.0);fragColor=vec4(vertexColor.rgb*pulse,vertexColor.a)*ColorModulator;}}
