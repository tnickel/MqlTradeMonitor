import React from "react";
import { AbsoluteFill, useCurrentFrame, useVideoConfig } from "remotion";

export const BootSequence: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const lines = [
    "CONNECTING TO BROKERS...",
    "ESTABLISHING ASYNC HEARTBEATS...",
    "INITIALIZING MQL TRADE MONITOR...",
    "SYSTEM BOOT: [OK]",
    "SECURITY AUDIT: [OK]"
  ];

  // Each line appears 30 frames after the previous one
  const visibleLines = lines.filter((_, i) => frame > i * 30);

  return (
    <AbsoluteFill
      style={{
        backgroundColor: "black",
        justifyContent: "center",
        alignItems: "flex-start",
        padding: "50px",
        fontFamily: "monospace",
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
        {visibleLines.map((line, i) => (
          <div
            key={i}
            style={{
              color: "#3fb950",
              fontSize: "40px",
              fontWeight: "bold",
            }}
          >
            {`> ${line}`}
          </div>
        ))}
        {/* Blinking cursor */}
        {Math.floor(frame / 15) % 2 === 0 && (
          <div
            style={{
              color: "#3fb950",
              fontSize: "40px",
              fontWeight: "bold",
              marginTop: "20px",
            }}
          >
            {`> _`}
          </div>
        )}
      </div>
    </AbsoluteFill>
  );
};
