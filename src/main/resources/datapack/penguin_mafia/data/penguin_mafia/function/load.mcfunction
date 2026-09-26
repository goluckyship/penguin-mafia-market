# Runs on datapack load / server start / every /reload
scoreboard objectives add frozen_coins dummy ["",{"text":"Frozen Coins","color":"aqua"}]
scoreboard objectives add blizzard_active dummy
scoreboard objectives add blizzard_timer dummy
scoreboard objectives add don_timer dummy
scoreboard objectives add rival_timer dummy
scoreboard objectives add feed_progress dummy
scoreboard objectives add rival_count dummy

# Only initialize the world-state fake player's scores if they don't exist yet,
# so a /reload mid-blizzard doesn't reset it.
execute unless score #world blizzard_timer matches -2147483648..2147483647 run scoreboard players set #world blizzard_timer 1200
execute unless score #world blizzard_active matches -2147483648..2147483647 run scoreboard players set #world blizzard_active 0
execute unless score #world don_timer matches -2147483648..2147483647 run scoreboard players set #world don_timer 2400
execute unless score #world rival_timer matches -2147483648..2147483647 run scoreboard players set #world rival_timer 600

tellraw @a [{"text":"[Penguin Mafia] ","color":"gold","bold":true},{"text":"data pack loaded.","color":"gray"}]
