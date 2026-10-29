// Posts, comments and profile details (033). English: the source; other languages use these keys (TypeScript
// refuses unknown ones) and a key they leave out reads in English. Loaded only with the screens that use them (components/social/feed-text.ts).
const en = {
  // a post
  'feed.loadFailed': 'Couldn’t load posts',
  'post.like': 'Like',
  'post.likes.one': '{n} like',
  'post.likes.other': '{n} likes',
  'post.comment': 'Comment',
  'post.comments.one': '{n} comment',
  'post.comments.other': '{n} comments',
  'post.menu': 'More options',
  'post.report': 'Report post',
  'post.delete': 'Delete post',
  'post.deleteTitle': 'Delete this post?',
  'post.deleteBody': 'It’s removed for everyone, with its likes and comments.',
  'post.deleted': 'Post deleted',
  'post.public': 'Everyone',
  'post.friendsOnly': 'Friends',
  'post.by': 'Post by {name}',
  'post.photo': 'Photo by {name}',
  'post.readMore': 'more',
  'post.gone': 'This post isn’t available',
  'post.goneBody': 'It was deleted, or only the author’s friends can see it.',
  'post.reportTitle': 'Report this post',
  'post.reported': 'Thanks. We’ll review this post.',
  'post.viewProfile': 'View profile',

  // comments
  'comments.title': 'Comments',
  'comments.none': 'No comments yet',
  'comments.noneBody': 'Start the conversation.',
  'comments.placeholder': 'Add a comment…',
  'comments.placeholderReply': 'Reply to {name}…',
  'comments.label': 'Your comment',
  'comments.send': 'Post',
  'comments.reply': 'Reply',
  'comments.replyingTo': 'Replying to {name}',
  'comments.cancelReply': 'Cancel reply',
  'comments.remove': 'Remove',
  'comments.removed': 'Comment removed',
  'comments.report': 'Report',
  'comments.reportTitle': 'Report this comment',
  'comments.reported': 'Thanks. We’ll review this comment.',
  'comments.more': 'Show more comments',
  'comments.loadFailed': 'Couldn’t load comments',

  // the composer
  'compose.audience': 'Who can see this',
  'compose.friends': 'Friends',
  'compose.everyone': 'Everyone',
  'compose.friendsHint': 'Your friends and people you’ve chatted with.',
  'compose.everyoneHint': 'Anyone on Yaari who opens your profile. They can also add you.',
  'compose.checking': 'Checking the photo…',
  'compose.nude': 'This photo looks like nudity, so it can’t be posted',

  // profile details

  // a profile
  'post.likers': "Likes",
}

export default en
