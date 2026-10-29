import type { FeedDict } from '../../feed-text'

const zhHans: FeedDict = {
  // the feed
  'feed.loadFailed': '无法加载动态',

  // a post
  'post.like': '赞',
  'post.likes.one': '{n} 个赞',
  'post.likes.other': '{n} 个赞',
  'post.comment': '评论',
  'post.comments.one': '{n} 条评论',
  'post.comments.other': '{n} 条评论',
  'post.menu': '更多选项',
  'post.report': '举报动态',
  'post.delete': '删除动态',
  'post.deleteTitle': '要删除这条动态吗？',
  'post.deleteBody': '它会连同点赞和评论一起对所有人删除。',
  'post.deleted': '动态已删除',
  'post.public': '所有人',
  'post.friendsOnly': '好友',
  'post.by': '{name} 的动态',
  'post.photo': '{name} 的照片',
  'post.readMore': '更多',
  'post.gone': '这条动态不可见',
  'post.goneBody': '它已被删除，或仅作者的好友可见。',
  'post.reportTitle': '举报这条动态',
  'post.reported': '谢谢，我们会审核这条动态。',
  'post.viewProfile': '查看主页',

  // comments
  'comments.title': '评论',
  'comments.none': '还没有评论',
  'comments.noneBody': '来发表第一条评论吧。',
  'comments.placeholder': '添加评论…',
  'comments.placeholderReply': '回复 {name}…',
  'comments.label': '你的评论',
  'comments.send': '发布',
  'comments.reply': '回复',
  'comments.replyingTo': '正在回复 {name}',
  'comments.cancelReply': '取消回复',
  'comments.remove': '删除',
  'comments.removed': '评论已删除',
  'comments.report': '举报',
  'comments.reportTitle': '举报这条评论',
  'comments.reported': '谢谢，我们会审核这条评论。',
  'comments.more': '显示更多评论',
  'comments.loadFailed': '无法加载评论',

  // the composer
  'compose.audience': '谁可以看',
  'compose.friends': '好友',
  'compose.everyone': '所有人',
  'compose.friendsHint': '你的好友和与你聊过天的人。',
  'compose.everyoneHint': 'yaari 上打开你主页的任何人都能看到。他们也可以加你好友。',
  'compose.checking': '正在检查照片…',
  'compose.nude': '这张照片疑似裸露内容，无法发布',

  // profile style

  // a profile
  'post.likers': "点赞",
}

export default zhHans
