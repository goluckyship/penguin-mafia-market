scoreboard players set #world blizzard_active 0
scoreboard players set #world blizzard_timer 1200
execute as @a[biome=penguin_mafia:mafia_peaks] run tellraw @s {"text":"The blizzard passes.","color":"gray","italic":true}
