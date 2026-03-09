# Redstone Interface
![Everything Red](block:oc2r:redstone_interface)

The redstone interface provides an omnidirectional bus to receive and emit redstone signals.

Use this to interact with primitive devices, such as doors and lamps, or other machinery offering a redstone based protocol.

This is a high level device. It must be controlled using the high level device API. The default Linux distribution offers Lua libraries for this API. For example:  
*local d = require("devices")*  
*local r = d:find("redstone")*  
*r:setRedstoneOutput("up", 15)*

## API
Device name: *redstone*

### Sides
The side parameter in the following methods represents a side of the device block. The valid values are listed below.
Some variations of these names, such as just using the first letter, also work, as do the numbers 0 through 9 for the
sides *down*, *up*, *north*, *south*, *west*, *east*, *back*, *front*, *left*, and *right* in that order.

Each face of the block has an indicator for convenience. Local side names represent the names with the block seen from the primary face (indicated by a single marking). When looking at the primary face:
- *front* is the face we are looking at (small dot).
- *back* is the face behind the block (a line and a dot).
- *left* is the face to our left (one line).
- *right* is the face to our right (two lines).
- *up* and *down* are the top and bottom faces (four dots on top, and no marking on bottom).
- *north*, *south*, *west*, and *east* are the sides in those absolute directions.

### Methods
*getRedstoneInput(side):number* gets the received redstone signal for the specified side.
- *side* is a string representing the side to get the input on. See the "Sides" section.
- Returns the number representing the current input signal strength.

*setRedstoneOutput(side, value:number)* sets the emitted redstone signal for the specified side.
- *side* is a string representing the side to set the signal on. See the "Sides" section.
- *value* is a number representing the signal strength to set in the range of [0, 15].

*getRedstoneOutput(side):number* gets the emitted redstone signal for the specified side.
- *side* is a string representing the side to get the output on. See the "Sides" section.
- Returns the number representing the current output signal strength.
