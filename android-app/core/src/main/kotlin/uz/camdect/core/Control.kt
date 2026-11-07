package uz.camdect.core

/**
 * Extra violations a camera checks besides speed, from flag bits in byte 21.
 * Meanings were inferred from the data (only the confident ones are listed):
 *  - bit 1: 3-4 traffic directions recorded at the same spot → intersection / red light
 *  - bit 3: one road, 1-2 directions, short range → pedestrian crossing
 *  - bit 6: almost never a speed limit, ~150 m range → no stopping / parking
 */
enum class Control(val bit: Int) {
    RED_LIGHT(1),
    CROSSWALK(3),
    NO_STOPPING(6),
}
