# Glossary

The words the features, the spec and the code use for one thing each.

- **Surface**: the representation of what a person last asked for, held on the server as an event
  sourced entity. It has a shape, may have an anchor, carries modifiers, may hold a pending act,
  keeps a rail, lists the devices showing it, and has a version.
- **Shape**: what the surface is showing, named by the consumer's vocabulary: a shelf, an item, a
  result. Not a screen: the same shape renders differently on each device class.
- **Anchor**: the thing the shape is about, by kind and id: a theme, a market, a person. The
  consumer's vocabulary names the kinds.
- **Modifier**: a named qualifier on the shape, by key and value: a time, an order, a stance. The
  vocabulary names the keys.
- **Pending**: an act the person has begun and not committed, held on the surface so every device
  shows it; committing it is the consumer's own route, never the surface's.
- **Move**: one change to the surface, with as many parts as it names, applied together.
- **Request**: the words a person said or typed that led to a move; kept on the rail.
- **Rail**: the last few requests, newest first, each with the surface it left; the way back.
- **Interpreter**: the consumer's function from the surface and a request's text to a move, or to
  a reason it could not make one.
- **Vocabulary**: what a consumer declares a surface may hold: the shapes, the anchor kinds, the
  modifier keys, the pending kinds, the device classes, the home shape and the rail's length.
- **Device**: anything rendering a surface, named by an id of its own and a device class.
- **Device class**: how a device renders: phone, laptop, tv, or whatever the vocabulary names.
- **Showing**: a device's record on the surface, with its class and whether it is in a room.
- **Room**: a device setting that marks it as showing the surface where others can see; a
  renderer hides what is personal.
- **Version**: a counter on the surface, up by one on every change recorded, by which a renderer
  orders what it is given.
- **Open**: to give a surface its owner and home shape; a surface not opened holds nothing and
  refuses every move.
- **Close**: to delete a surface, freeing its id.
