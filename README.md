# spacebot

IRC bot for hackerdeen

## Installation

Get from github.com/ormiret/spacebot

## Configuration

Create a configuration file core.clj something like
       {:nick "hackerdeenbot"
        :channels ["#hackerdeen" "#57N"]
	}

## Usage

lein run

## Deployment

1. Make a jar with `lein uberjar`
1. Copy the jar to the machine you want to run on.
1. Run the bot with `java -jar <jar file>`

The bot will eat memory if you let it. You can add something like
`-Xmx200m` to the `java` command to limit it's heap to 200MiB.

## License

Copyright © 2013 Robert McWilliam

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
