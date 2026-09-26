tag @s add is_tamed
data modify entity @s CustomName set value '{"text":"Loyal Penguin \uD83D\uDC27","color":"light_purple"}'
particle minecraft:heart ~ ~1 ~ 0.3 0.3 0.3 0.01 8
playsound minecraft:entity.chicken.egg master @a[distance=..8] ~ ~ ~ 1.0 1.2
execute as @a[distance=..3] run advancement grant @s only penguin_mafia:tame_penguin
