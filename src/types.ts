export interface TrackItem {
  id: string;
  title: string;
  artist: string;
  artworkUrl: string;
  transcodingsUrl: string | null;
  duration: number;
}
