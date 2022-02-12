#version 150

#moj_import <fog.glsl>

uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
flat in vec4 vertexColor;

out vec4 fragColor;

void main() {
    //Fix by DartCat25

    if (vertexColor.a == 0.0)
        discard;

    fragColor = linear_fog(vertexColor, vertexDistance, FogStart, FogEnd, FogColor);
}
