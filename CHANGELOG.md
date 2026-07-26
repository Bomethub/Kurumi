# Changelog

## 1.0.20

- Added a three-entry short-lived placement candidate queue.
- Prioritized the nearest missing block on the active SL ray.
- Updated repeated samples of the same target with the newest real hit point.
- Kept required X-face start placement above normal ray candidates.
- Added immediate current-hit fallback when cached candidates become invalid.
- Added one-block virtual lookahead based on the current real view ray and vanilla reach.
- Preserved one real placement attempt per client tick.
- Preserved the original local view without silent rotation or mouse modification.
- Kept surrounding-support search and fallback placement disabled.

## 1.0.14.1

- Captured the current vanilla block hit at the end of a render tick.
- Consumed one short-lived placement candidate at the start of the player tick.
- Used the vanilla right-click placement method and swing animation.
- Kept FastPlace active while StraightLine was active.
- Limited placement to one real attempt per client tick.
- Fixed the runtime mapping of the three `Vec3` coordinate fields.
