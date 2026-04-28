import React from "react";
import { AbsoluteFill, useCurrentFrame, spring, useVideoConfig, interpolate, Sequence } from "remotion";

const FadeText: React.FC<{ text: string, delay: number }> = ({ text, delay }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = interpolate(frame - delay, [0, 15], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const translateY = interpolate(spring({ frame: frame - delay, fps, config: { damping: 14 } }), [0, 1], [50, 0]);

  return (
    <div style={{ opacity, transform: `translateY(${translateY}px)`, fontSize: "70px", fontWeight: 900, color: "white", letterSpacing: "5px", marginBottom: "20px" }}>
      {text}
    </div>
  );
};

export const TypographyFinale: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // Fade out everything at the end
  const finalOpacity = interpolate(frame, [400, 430], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <AbsoluteFill
      style={{
        backgroundColor: "black",
        justifyContent: "center",
        alignItems: "center",
        fontFamily: "sans-serif",
        opacity: finalOpacity
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
        <FadeText text="ZERO LATENCY." delay={10} />
        <FadeText text="MILITARY GRADE SECURITY." delay={50} />
        <FadeText text="FULL-STACK ENGINEERING." delay={90} />

        {/* Final Logo / Name */}
        <div style={{ 
          marginTop: "100px", 
          opacity: interpolate(frame - 150, [0, 30], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }),
          fontSize: "40px",
          color: "#8b949e",
          letterSpacing: "2px"
        }}>
          MQL Trade Monitor. Engineered by <span style={{ color: "white", fontWeight: "bold" }}>Thomas Nickel</span>.
        </div>
      </div>
    </AbsoluteFill>
  );
};
