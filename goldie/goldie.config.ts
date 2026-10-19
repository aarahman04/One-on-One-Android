import { fileURLToPath } from "node:url";

export default {
  appRoot: fileURLToPath(new URL("../", import.meta.url)),
  appPath: "../app/build/outputs/apk/debug/app-debug.apk",
  bundleId: "app.web.oneonone",
  android: {
    appPath: "../app/build/outputs/apk/debug/app-debug.apk",
    applicationId: "app.web.oneonone",
  },
  devices: ["pixel-10-pro"],
  locales: ["en-US"],
  appearance: "dark",
  frame: { variant: {} },
  theme: {
    background: "linear-gradient(155deg, #122C32 0%, #102331 52%, #101725 100%)",
    headlineColor: "#F2F7F5",
    subheadColor: "#B9CFD0",
    fontFamily: "Figtree, sans-serif",
    fontFiles: [{
      family: "Figtree",
      files: {
        400: "../app/src/main/res/font/figtree_regular.ttf",
        600: "../app/src/main/res/font/figtree_semibold.ttf",
        700: "../app/src/main/res/font/figtree_bold.ttf",
      },
    }],
    copyHeightRatio: 0.23,
    deviceWidthRatio: 0.85,
    template: ["classic", "classic", "offset", "classic", "tilt"],
  },
  store: {
    name: "One on One",
    subtitle: { "en-US": "Stay close to your person" },
    developer: "One on One",
    category: "Communication",
    rating: 0,
    ratingCount: "Not rated",
    ageRating: "18+",
    price: "",
    description: { "en-US": "A private space for two people. Share everyday messages, write letters, check in on each other, make playful choices and count down to your next moment together." },
  },
  scenes: [
    {
      kind: "screenshot", id: "01-chat", flow: "store-01-chat",
      headline: { "en-US": "Your person. Your space." },
      subhead: { "en-US": "A conversation made for just the two of you." },
    },
    {
      kind: "screenshot", id: "02-letter", flow: "store-02-letter",
      headline: { "en-US": "Say it with a letter" },
      subhead: { "en-US": "Make room for words worth keeping." },
      background: "linear-gradient(155deg, #323346 0%, #20283C 55%, #152131 100%)",
    },
    {
      kind: "screenshot", id: "03-alarm", flow: "store-03-alarm",
      headline: { "en-US": "Be there when it matters" },
      subhead: { "en-US": "Send an emergency alarm when you need them." },
      layout: "duo-tilt",
      secondScene: "02-letter",
    },
    {
      kind: "screenshot", id: "04-choices", flow: "store-04-choices",
      headline: { "en-US": "Little choices, together" },
      subhead: { "en-US": "Pick your favourites. Reveal them together." },
      background: "linear-gradient(155deg, #173A43 0%, #173246 55%, #162032 100%)",
    },
    {
      kind: "screenshot", id: "05-countdown", flow: "store-05-countdown",
      headline: { "en-US": "Closer with every second" },
      subhead: { "en-US": "Count down to the moments that matter." },
    },
    {
      kind: "screenshot", id: "06-stickers", flow: "store-06-stickers",
      headline: { "en-US": "A little more you" },
      subhead: { "en-US": "Say it with cats, hearts and your own stickers." },
      layout: "classic",
      background: "linear-gradient(155deg, #263B3B 0%, #18323B 55%, #132231 100%)",
    },
    {
      kind: "screenshot", id: "07-calls", flow: "store-07-calls",
      headline: { "en-US": "Feel close, from anywhere" },
      subhead: { "en-US": "Voice or video. Just the two of you." },
      layout: "classic",
      background: "linear-gradient(155deg, #373138 0%, #282D3D 55%, #172331 100%)",
    },
  ],
};
