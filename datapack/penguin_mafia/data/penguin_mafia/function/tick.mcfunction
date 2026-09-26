# Runs every tick. Only touches chickens inside the two custom biomes.
# Regular chickens everywhere else on the server are never affected.
execute as @e[type=minecraft:chicken,tag=!is_penguin] at @s if biome ~ ~ ~ penguin_mafia:mafia_tundra run function penguin_mafia:tag_penguin
execute as @e[type=minecraft:chicken,tag=!is_penguin] at @s if biome ~ ~ ~ penguin_mafia:mafia_peaks run function penguin_mafia:tag_penguin

# Taming: feed a penguin bread up close to win it over
function penguin_mafia:tame/check

# Tamed penguins gently "snap" back if they wander too far from every player
function penguin_mafia:tame/follow

# Blizzards over the peaks
function penguin_mafia:blizzard/tick

# Rare boss spawn ("The Don")
function penguin_mafia:don/tick

# Rival gang patrols
function penguin_mafia:rival/tick
