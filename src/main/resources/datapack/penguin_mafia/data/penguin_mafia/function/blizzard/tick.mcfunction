scoreboard players remove #world blizzard_timer 1
execute if score #world blizzard_timer matches ..0 run function penguin_mafia:blizzard/toggle
execute if score #world blizzard_active matches 1 run function penguin_mafia:blizzard/apply
