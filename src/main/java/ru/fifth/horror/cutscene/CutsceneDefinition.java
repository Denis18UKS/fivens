package ru.fifth.horror.cutscene;

import java.util.ArrayList;
import java.util.List;

public class CutsceneDefinition {
    public String id = "scene";
    public boolean hideHud = true;
    public boolean lockInput = true;
    /** If true, each player is teleported so their eyes land on the final camera keyframe when playback finishes. */
    public boolean teleportPlayerAtEnd = false;
    public List<Keyframe> keyframes = new ArrayList<>();

    public static class Keyframe {
        public double x,y,z;
        public float yaw,pitch;
        public double fov = 70.0;
        public int durationTicks = 40;
        public String subtitle = "";
        public String event = "";
        public Keyframe() {}
        public Keyframe(double x,double y,double z,float yaw,float pitch,double fov,int durationTicks){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;this.fov=fov;this.durationTicks=durationTicks;}
    }
}
