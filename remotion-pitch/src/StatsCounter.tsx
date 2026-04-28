import React from "react";
import { AbsoluteFill, useCurrentFrame, spring, useVideoConfig } from "remotion";

export const StatsCounter: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Animate values over time
  const accountsCount = Math.round(spring({ frame, fps, config: { damping: 200 }, durationInFrames: 60 }) * 42);
  const tradesCount = Math.round(spring({ frame: frame - 15, fps, config: { damping: 200 }, durationInFrames: 60 }) * 345);
  const latency = Math.max(4, Math.round(100 - spring({ frame: frame - 30, fps, config: { damping: 200 }, durationInFrames: 60 }) * 96));

  return (
    <AbsoluteFill
      style={{
        backgroundColor: "#0d1117",
        justifyContent: "center",
        alignItems: "center",
        fontFamily: "sans-serif",
        color: "white",
      }}
    >
      <div style={{ display: "flex", flexDirection: "row", gap: "100px", textAlign: "center" }}>
        
        {/* Terminals */}
        <div style={{ opacity: frame > 0 ? 1 : 0 }}>
          <div style={{ fontSize: "120px", fontWeight: "bold", color: "#58a6ff" }}>{accountsCount}</div>
          <div style={{ fontSize: "30px", color: "#8b949e", textTransform: "uppercase", letterSpacing: "2px" }}>Connected Terminals</div>
        </div>

        {/* Trades */}
        <div style={{ opacity: frame > 15 ? 1 : 0 }}>
          <div style={{ fontSize: "120px", fontWeight: "bold", color: "#a371f7" }}>{tradesCount}</div>
          <div style={{ fontSize: "30px", color: "#8b949e", textTransform: "uppercase", letterSpacing: "2px" }}>Active Trades</div>
        </div>

        {/* Latency */}
        <div style={{ opacity: frame > 30 ? 1 : 0 }}>
          <div style={{ fontSize: "120px", fontWeight: "bold", color: latency <= 10 ? "#3fb950" : "#f85149" }}>{latency}ms</div>
          <div style={{ fontSize: "30px", color: "#8b949e", textTransform: "uppercase", letterSpacing: "2px" }}>Network Latency</div>
        </div>

      </div>

      <div style={{
        marginTop: "100px",
        fontSize: "40px",
        color: "#8b949e",
        opacity: Math.sin(frame / 5) > 0 ? 1 : 0.2
      }}>
        ASYNC WORKER THREADS: RUNNING
      </div>
    </AbsoluteFill>
  );
};
