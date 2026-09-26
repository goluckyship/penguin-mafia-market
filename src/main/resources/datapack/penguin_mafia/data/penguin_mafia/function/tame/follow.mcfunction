# Vanilla chickens have no "follow owner" AI goal, and data packs can't add new AI
# goals to a mob (that needs a plugin/mod). This is a workaround: if no player is
# within 10 blocks, teleport the penguin to whichever player is nearest, if any
# are within 40 blocks. It won't path around obstacles like real follow AI would.
execute as @e[tag=is_tamed] at @s unless entity @a[distance=..10] if entity @a[distance=..40] run function penguin_mafia:tame/snap_to_owner
