import type { FeedDict } from '../../feed-text'

const ru: FeedDict = {
  // the feed
  'feed.loadFailed': 'Не удалось загрузить посты',

  // a post
  'post.like': 'Нравится',
  'post.likes.one': '{n} отметка «Нравится»',
  'post.likes.few': '{n} отметки «Нравится»',
  'post.likes.many': '{n} отметок «Нравится»',
  'post.likes.other': '{n} отметки «Нравится»',
  'post.comment': 'Комментировать',
  'post.comments.one': '{n} комментарий',
  'post.comments.few': '{n} комментария',
  'post.comments.many': '{n} комментариев',
  'post.comments.other': '{n} комментария',
  'post.menu': 'Ещё',
  'post.report': 'Пожаловаться на пост',
  'post.delete': 'Удалить пост',
  'post.deleteTitle': 'Удалить этот пост?',
  'post.deleteBody': 'Он будет удалён для всех вместе с лайками и комментариями.',
  'post.deleted': 'Пост удалён',
  'post.public': 'Все',
  'post.friendsOnly': 'Друзья',
  'post.by': 'Пост: {name}',
  'post.photo': 'Фото: {name}',
  'post.readMore': 'ещё',
  'post.gone': 'Этот пост недоступен',
  'post.goneBody': 'Его удалили, или его могут видеть только друзья автора.',
  'post.reportTitle': 'Пожаловаться на пост',
  'post.reported': 'Спасибо. Мы проверим этот пост.',
  'post.viewProfile': 'Открыть профиль',

  // comments
  'comments.title': 'Комментарии',
  'comments.none': 'Комментариев пока нет',
  'comments.noneBody': 'Начните обсуждение.',
  'comments.placeholder': 'Добавьте комментарий…',
  'comments.placeholderReply': 'Ответ: {name}…',
  'comments.label': 'Ваш комментарий',
  'comments.send': 'Опубликовать',
  'comments.reply': 'Ответить',
  'comments.replyingTo': 'Ответ: {name}',
  'comments.cancelReply': 'Отменить ответ',
  'comments.remove': 'Удалить',
  'comments.removed': 'Комментарий удалён',
  'comments.report': 'Пожаловаться',
  'comments.reportTitle': 'Пожаловаться на комментарий',
  'comments.reported': 'Спасибо. Мы проверим этот комментарий.',
  'comments.more': 'Показать ещё комментарии',
  'comments.loadFailed': 'Не удалось загрузить комментарии',

  // the composer
  'compose.audience': 'Кто это увидит',
  'compose.friends': 'Друзья',
  'compose.everyone': 'Все',
  'compose.friendsHint': 'Ваши друзья и люди, с которыми вы общались.',
  'compose.everyoneHint': 'Все в yaari, кто откроет ваш профиль. Они также смогут добавить вас.',
  'compose.checking': 'Проверяем фото…',
  'compose.nude': 'Похоже, на фото нагота, поэтому его нельзя опубликовать',

  // profile style

  // a profile
  'post.likers': "Отметки «Нравится»",
}

export default ru
