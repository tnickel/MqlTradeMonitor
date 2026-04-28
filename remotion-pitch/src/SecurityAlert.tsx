import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig, spring, interpolate } from "remotion";

export const SecurityAlert: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Flashing red background
  const isAlarm = frame < 60;
  const flash = isAlarm ? Math.sin(frame / 2) > 0 : false;
  const bgColor = flash ? "#8a0000" : "#0d1117";

  // Text animation
  const showWarning = frame > 10;
  const showDefense = frame > 70;
  const showCounter = frame > 100;

  const bannedCount = Math.round(spring({ frame: frame - 100, fps, config: { damping: 200 }, durationInFrames: 60 }) * 9);
  
  const scale = interpolate(spring({ frame: frame - 10, fps, config: { damping: 12 } }), [0, 1], [0.8, 1]);

  return (
    <AbsoluteFill
      style={{
        backgroundColor: bgColor,
        justifyContent: "center",
        alignItems: "center",
        fontFamily: "sans-serif",
        color: "white",
        textAlign: "center"
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "40px", transform: `scale(${scale})` }}>
        
        {/* Warning */}
        {showWarning && (
          <div style={{ fontSize: "60px", fontWeight: "bold", color: "#f85149", letterSpacing: "4px" }}>
            [!] WARNING: UNAUTHORIZED SSH ACCESS DETECTED
          </div>
        )}

        {/* Defense */}
        {showDefense && (
          <div style={{ fontSize: "50px", fontWeight: "bold", color: "#58a6ff" }}>
            OS-LEVEL DEFENSE: FAIL2BAN INTEGRATION ACTIVE
          </div>
        )}

        {/* Counter */}
        {showCounter && (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", marginTop: "40px" }}>
            <div style={{ fontSize: "160px", fontWeight: "bold", color: "#f85149" }}>{bannedCount}</div>
            <div style={{ fontSize: "40px", color: "#8b949e", textTransform: "uppercase", letterSpacing: "3px" }}>ATTACKER IPs BANNED</div>
          </div>
        )}

      </div>
    </AbsoluteFill>
  );
};
