import { AbsoluteFill, Sequence } from "remotion";
import { BootSequence } from "./BootSequence";
import { StatsCounter } from "./StatsCounter";
import { SecurityAlert } from "./SecurityAlert";
import { TypographyFinale } from "./TypographyFinale";

export const MyComposition = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: "black" }}>
      {/* Szene 1: Boot Sequence (0 - 10s) = 300 frames */}
      <Sequence from={0} durationInFrames={300}>
        <BootSequence />
      </Sequence>

      {/* Szene 2: Skalierbarkeit & Latenz (10s - 25s) = 450 frames */}
      <Sequence from={300} durationInFrames={450}>
        <StatsCounter />
      </Sequence>

      {/* Szene 3: Cyber Defense (25s - 45s) = 600 frames */}
      <Sequence from={750} durationInFrames={600}>
        <SecurityAlert />
      </Sequence>

      {/* Szene 4: Finale Hook (45s - 60s) = 450 frames */}
      <Sequence from={1350} durationInFrames={450}>
        <TypographyFinale />
      </Sequence>
    </AbsoluteFill>
  );
};
