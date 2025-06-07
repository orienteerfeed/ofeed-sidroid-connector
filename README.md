# OFeed SI-Droid Connector

This Android app uploads result lists from [SI-Droid Event](https://play.google.com/store/apps/details?id=se.joja.sidroid.event.full) to [OFeed](https://orienteerfeed.com/).

## Features

- Uploads result lists from SI-Droid Event to OFeed.
- Uses [IOF Data Standard 3.0](https://orienteering.sport/iof/it/data-standard-3-0/).
- Supports OFeed QR codes for easy configuration.

## Development

### Prerequisites

- Android Studio
- Android 7+ (minSDK 24, targetSDK 35)
- Java 11

### Installation

1. Clone this repository:

```bash
   git clone https://github.com/orienteerfeed/ofeed-sidroid-connector.git
```

2. Open the project in Android Studio

3. Let Gradle sync and build the project

4. Run the app on a connected device

### Built With

- [OkHttp](https://square.github.io/okhttp/) - Networking library

## License

This project is released as open source licensed under the Apache License 2.0. See the NOTICE file for third-party acknowledgments.

## Contributing

Contributions are welcome! To contribute:

1.  Fork the repository

2.  Create a new branch: `git checkout -b feature/YourFeature`

3.  Commit your changes: `git commit -m 'Add your feature'`

4.  Push to the branch: `git push origin feature/YourFeature`

5.  Submit a pull request

## Credits

- [Lukáš Kettner](https://github.com/lukaskett)
- [Anders Löfgren](https://github.com/AnLof)

## Contact

If you have questions or feedback, feel free to reach out via [email](mailto:connector@stigning.se?subject=OFeed%20SI-Droid%20Connector) or open an issue on GitHub.
