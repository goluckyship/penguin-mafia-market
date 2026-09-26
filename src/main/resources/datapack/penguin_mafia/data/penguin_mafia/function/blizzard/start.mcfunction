scoreboard players set #world blizzard_active 1
scoreboard players set #world blizzard_timer 600
execute as @a[biome=penguin_mafia:mafia_peaks] run tellraw @s [{"text":"❄ ","color":"aqua"},{"text":"A blizzard rolls in over the Peaks...","color":"aqua","italic":true}]
execute as @a[biome=penguin_mafia:mafia_peaks] run playsound minecraft:weather.rain ambient @s ~ ~ ~ 1.0 0.6
