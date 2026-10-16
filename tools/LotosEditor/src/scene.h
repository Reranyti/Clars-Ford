#pragma once
#include "gl.h"
#include "math3d.h"

#include <string>
#include <vector>

// a place the picture is drawn into (the viewport and the thumbnails of the storyboard)
struct Target {
    GLuint fbo = 0, tex = 0, depth = 0;
    int w = 0, h = 0;
    void ensure(int width, int height);
};

struct Camera {
    V3 pos{0, 18, 60};
    V3 target{0, 16, 0};
    float fov = 40;
    float roll = 0;
};

// what the picture looks like: the look of the game's close-up (cold light, blue edge), with a few knobs
struct Look {
    float posterize = 0;      // 0 off; else the number of levels of every colour (the original is posterized)
    float rim = 0.8f;
    float fog = 0.004f;
    bool grid = true;
    float time = 0;
};

struct Scene {
    bool init(const std::string& dataDir, std::string& error);
    void render(Target& t, const Camera& cam, const Look& look);
    int faceCount = 0;

private:
    GLuint progFigure = 0, progGrid = 0, vaoFigure = 0, vboFigure = 0, vaoGrid = 0, vboGrid = 0, skin = 0;
    int vertCount = 0;
};
